
package com.gupao.edu.vip.lion.network.netty.connection;

import com.gupao.edu.vip.lion.api.connection.Connection;
import com.gupao.edu.vip.lion.api.connection.SessionContext;
import com.gupao.edu.vip.lion.api.protocol.Packet;
import com.gupao.edu.vip.lion.api.spi.core.RsaCipherFactory;
import com.gupao.edu.vip.lion.tools.log.Logs;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * cny_note Connection其实是对netty channel的一层包装
 */
public final class NettyConnection implements Connection, ChannelFutureListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(NettyConnection.class);
    private SessionContext context;
    private Channel channel;
    private volatile byte status = STATUS_NEW;
    private long lastReadTime;
    private long lastWriteTime;

    @Override
    public void init(Channel channel, boolean security) {
        this.channel = channel;
        this.context = new SessionContext();
        this.lastReadTime = System.currentTimeMillis();//？？此处应该不用更新读时间
        this.status = STATUS_CONNECTED;
        if (security) { //cny_note 如果security设置为true 则使用当前connection实例发送消息的时候回对消息进行加密（用RsaCipherFactory.create()得到的加密工具加密）
            this.context.changeCipher(RsaCipherFactory.create());
        }
    }

    @Override
    public void setSessionContext(SessionContext context) { //？？sessionContext是在哪里初始化内部相关属性的
        this.context = context;
    }

    @Override
    public SessionContext getSessionContext() {
        return context;
    }

    @Override
    public String getId() {
        return channel.id().asShortText();//？？asShortText()返回的是非全局唯一，为什么还要用
    }

    @Override
    public ChannelFuture send(Packet packet) {
        return send(packet, null);
    }

    @Override
    public ChannelFuture send(Packet packet, final ChannelFutureListener listener) {
        if (channel.isActive()) {//这里的channel可以是client的也可以是server的

            ChannelFuture future = channel.writeAndFlush(packet.toFrame(channel)).addListener(this);//cny_note 回调operationComplete更新自身内部状态

            if (listener != null) {//？这里channel.writeAndFlush(packet.toFrame(channel))返回的ChannelFuture应该就是future
                future.addListener(listener);
            }

            if (channel.isWritable()) {
                return future;
            }

            /*cny_note 阻塞调用线程还是抛异常？
            return channel.newPromise().setFailure(new RuntimeException("send data too busy"));
            ？这个判断是否在线程池内，难道还有线程池外的？
            注：inEventLoop表示启动线程与当前线程相同，相同表示已经启动，不同则有两种可能：未启动或者线程不同
            ！个人认为主要是在封装channel的时候，有可能使得channel从原本绑定的线程传递到别的线程，
            ！此时别的线程来调用相关读写操作时就会出问题，所以就需要做安全处理*/
            if (!future.channel().eventLoop().inEventLoop()) {
                //等待100ms，拿到结果就拿到，拿不到结果会报错。也就是说设置100内基本是无可能拿到结果，所以会直接报错。
                //注意这里的100是经验值，一般不要超过500ms
                //cny_note ChannelFuture原理使用详见CSDN 《netty理论之源码分析⑦ 细讲future和Channelfuture》
                future.awaitUninterruptibly(100);//cny_note 注意future.await()不能在io线程即netty通道线程里使用，否则会抛出一个异常，避免死锁
            }
            return future;
        } else {
            /*if (listener != null) {
                channel.newPromise()
                        .addListener(listener)
                        .setFailure(new RuntimeException("connection is disconnected"));
            }*/
            return this.close();
        }
    }

    @Override
    public ChannelFuture close() {
        if (status == STATUS_DISCONNECTED) return null;
        this.status = STATUS_DISCONNECTED;
        return this.channel.close();
    }

    @Override
    public boolean isConnected() {
        return status == STATUS_CONNECTED;
    }

    /**
     * cny_note 可以配置一个读超时时间，如果超过这个时间，则认定为读超时
     * 此时使用此连接的人就可以根据是否读超时，进行一些操作，如3次超时则调用connection.close();
     * 写超时的作用同理
     * 注意：如果大于号右边的式子值比心跳间隔时间小context.heartbeat则心跳检查可能会出现检查不出读超时
     *       因为会出现每次等到检查的"前一刻"就出现了一次读操作，并且更新lastReadTime，这样就检查不出读超时了。
     * @return
     */
    @Override
    public boolean isReadTimeout() {
        return System.currentTimeMillis() - lastReadTime > context.heartbeat + 1000;
    }

    /**
     * ？写超时逻辑有点搞不懂，应该是要+1000才对吧。大白说后面会再解释
     * @return
     */
    @Override
    public boolean isWriteTimeout() {
        return System.currentTimeMillis() - lastWriteTime > context.heartbeat - 1000;
    }

    /**
     * cny_note ？个人认为更新读时间不应暴露给业务的handler（如：ConnClientChannelHandler）去更新，而应该在当前connection对应的channel的pipeline里注册handler处理
     */
    @Override
    public void updateLastReadTime() {
        lastReadTime = System.currentTimeMillis();
    }

    /**
     * 写数据成功的回调
     * 此方法是爷爷接口GenericFutureListener规定的方法
     * @param future
     * @throws Exception
     */
    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
            lastWriteTime = System.currentTimeMillis();
        } else {
            LOGGER.error("connection send msg error", future.cause());
            Logs.CONN.error("connection send msg error={}, conn={}", future.cause().getMessage(), this);
        }
    }

    @Override
    public void updateLastWriteTime() {
        lastWriteTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "[channel=" + channel
                + ", context=" + context
                + ", status=" + status
                + ", lastReadTime=" + lastReadTime
                + ", lastWriteTime=" + lastWriteTime
                + "]";
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NettyConnection that = (NettyConnection) o;

        return channel.id().equals(that.channel.id());
    }

    @Override
    public int hashCode() {
        return channel.id().hashCode();
    }
}
