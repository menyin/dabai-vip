
package com.gupao.edu.vip.lion.core.server;


import com.gupao.edu.vip.lion.api.connection.Connection;
import com.gupao.edu.vip.lion.api.connection.ConnectionManager;
import com.gupao.edu.vip.lion.network.netty.connection.NettyConnection;
import com.gupao.edu.vip.lion.tools.config.CC;
import com.gupao.edu.vip.lion.tools.log.Logs;
import com.gupao.edu.vip.lion.tools.thread.NamedThreadFactory;
import com.gupao.edu.vip.lion.tools.thread.ThreadNames;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.util.HashedWheelTimer;
import io.netty.util.Timeout;
import io.netty.util.TimerTask;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;


/**
 * 是一个连接的管理类
 *  1.实现了ConnectionManager规定的基本连接的curd
 *  2.如果配置了心跳检查，则在add 一个连接时进行一个包装，这个包装里通过定时器对connection的状态进行检查，
 *  并进行一些操作，如记录日志、读超时3次则关闭连接。这样做的好处就是关闭掉长时间没有读操作的连接或者说是通道
 */
public final class ServerConnectionManager implements ConnectionManager {
    /**
     * cny_note connections是一个连接的容器，里面的ConnectionHolder是对连接的进一步包装
     */
    private final ConcurrentMap<ChannelId, ConnectionHolder> connections = new ConcurrentHashMap<>();
    private final ConnectionHolder DEFAULT = new SimpleConnectionHolder(null);
    private final boolean heartbeatCheck;
    private final ConnectionHolderFactory holderFactory;
    private HashedWheelTimer timer;

    public ServerConnectionManager(boolean heartbeatCheck) {
        this.heartbeatCheck = heartbeatCheck;
        this.holderFactory = heartbeatCheck ? HeartbeatCheckTask::new : SimpleConnectionHolder::new;
    }

    @Override
    public void init() {
        if (heartbeatCheck) {//？？这里为什么不用channel.eventLoop().execute()去执行心跳检测任务，而是用NamedThreadFactory例去new一个Thread
            long tickDuration = TimeUnit.SECONDS.toMillis(1);//1s 每秒钟走一步，一个心跳周期内大致走一圈 //cny tickDuration应该算刻度
            int ticksPerWheel = (int) (CC.lion.core.max_heartbeat / tickDuration);//cny_note max_heartbeat算一个时钟周期（和24h同理），ticksPerWheel一个时间周期有几个刻度
            this.timer = new HashedWheelTimer(//？？这个timer要学习下使用
                    new NamedThreadFactory(ThreadNames.T_CONN_TIMER),//cny_note NamedThreadFactory主要是得到一个守护线程，以防止："主线程等非守护线程退出，这个timer还在跑"的情况发生
                    tickDuration, TimeUnit.MILLISECONDS, ticksPerWheel
            );
        }
    }

    @Override
    public void destroy() {
        if (timer != null) {
            timer.stop();
        }
        connections.values().forEach(ConnectionHolder::close);
        connections.clear();
    }

    @Override
    public Connection get(Channel channel) {
        //？？首次骑手客户端连接时，DEFAULT里的connection=null，这时候整个方法范围null，在HandshakeHandler#handle方法似乎有问题
        //！！不会有问题因为在HandshakeHandler#handle之前会会执行ServerChannelHandler#channelActive把新连入的connection存储起来
        return connections.getOrDefault(channel.id(), DEFAULT).get();
    }

    @Override
    public void add(Connection connection) {
        connections.putIfAbsent(connection.getChannel().id(), holderFactory.create(connection));
    }

    @Override
    public Connection removeAndClose(Channel channel) {
        ConnectionHolder holder = connections.remove(channel.id());//从Map中移除了元素
        if (holder != null) {
            Connection connection = holder.get();
            holder.close();
            return connection;
        }

        //add default cny_note 如果在连接容器里没有对应连接，则根据通道创建连接并将其关闭，返回这个关闭状态的连接
        Connection connection = new NettyConnection();
        connection.init(channel, false);
        connection.close();
        return connection;
    }

    @Override
    public int getConnNum() {
        return connections.size();
    }

    private interface ConnectionHolder {
        Connection get();

        void close();
    }

    private static class SimpleConnectionHolder implements ConnectionHolder {
        private final Connection connection;

        private SimpleConnectionHolder(Connection connection) {
            this.connection = connection;
        }

        @Override
        public Connection get() {
            return connection;
        }

        @Override
        public void close() {
            if (connection != null) {
                connection.close();
            }
        }
    }

    /**
     * HeartbeatCheckTask其实是一个ConnectionHolder接口实例
     * 并且也是TimerTask接口实例
     * 这样就对于HeartbeatCheckTask实例一般是公共ConnectionHolder接收并调用该接口的api
     * 而TimerTask只是作为附加的内部隐藏的功能的内部规约
     */
    private class HeartbeatCheckTask implements ConnectionHolder, TimerTask {

        private byte timeoutTimes = 0;
        private Connection connection;

        private HeartbeatCheckTask(Connection connection) {
            this.connection = connection;
            this.startTimeout();
        }

        void startTimeout() {
            Connection connection = this.connection;

            if (connection != null && connection.isConnected()) {
                int timeout = connection.getSessionContext().heartbeat;
                timer.newTimeout(this, timeout, TimeUnit.MILLISECONDS);//cny_note 注意多有的connection都是用同一个timer实例
            }
        }

        @Override
        public void run(Timeout timeout) throws Exception {
            Connection connection = this.connection;

            if (connection == null || !connection.isConnected()) {
                Logs.HB.info("heartbeat timeout times={}, connection disconnected, conn={}", timeoutTimes, connection);
                return;
            }

            if (connection.isReadTimeout()) {//cny_note 当出现nettyConnection的读超时超过3次则将连接关闭掉，主要就是清理掉长时间没读操作的通道。
                if (++timeoutTimes > CC.lion.core.max_hb_timeout_times) {
                    connection.close();
                    Logs.HB.warn("client heartbeat timeout times={}, do close conn={}", timeoutTimes, connection);
                    return;
                } else {
                    Logs.HB.info("client heartbeat timeout times={}, connection={}", timeoutTimes, connection);
                }
            } else {
                timeoutTimes = 0;
            }
            startTimeout();//cny_note 递归，其实就是循环定时任务
        }

        @Override
        public void close() {
            if (connection != null) {
                connection.close();
                connection = null;
            }
        }

        /**
         * HeartbeatCheckTask是一个ConnectionHolder接口实例
         * 所以最终this.holderFactory = heartbeatCheck ? HeartbeatCheckTask::new
         * 最终其实会这样使用holderFactory.create(connection).get()，得到一个connection
         * 而
         * @return
         */
        @Override
        public Connection get() {
            return connection;
        }
    }

    @FunctionalInterface
    private interface ConnectionHolderFactory {
        ConnectionHolder create(Connection connection);
    }
}
