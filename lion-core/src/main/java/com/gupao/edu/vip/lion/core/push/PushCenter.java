
package com.gupao.edu.vip.lion.core.push;

import com.gupao.edu.vip.lion.api.service.BaseService;
import com.gupao.edu.vip.lion.api.service.Listener;
import com.gupao.edu.vip.lion.api.spi.push.IPushMessage;
import com.gupao.edu.vip.lion.api.spi.push.MessagePusher;
import com.gupao.edu.vip.lion.api.spi.push.PushListener;
import com.gupao.edu.vip.lion.api.spi.push.PushListenerFactory;
import com.gupao.edu.vip.lion.common.qps.FastFlowControl;
import com.gupao.edu.vip.lion.common.qps.FlowControl;
import com.gupao.edu.vip.lion.common.qps.GlobalFlowControl;
import com.gupao.edu.vip.lion.common.qps.RedisFlowControl;
import com.gupao.edu.vip.lion.core.LionServer;
import com.gupao.edu.vip.lion.core.ack.AckTaskQueue;
import com.gupao.edu.vip.lion.monitor.jmx.MBeanRegistry;
import com.gupao.edu.vip.lion.monitor.jmx.mxbean.PushCenterBean;
import com.gupao.edu.vip.lion.tools.config.CC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.gupao.edu.vip.lion.tools.config.CC.lion.push.flow_control.broadcast.*;


public final class PushCenter extends BaseService implements MessagePusher {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final GlobalFlowControl globalFlowControl = new GlobalFlowControl(
            CC.lion.push.flow_control.global.limit, CC.lion.push.flow_control.global.max, CC.lion.push.flow_control.global.duration
    );

    private final AtomicLong taskNum = new AtomicLong();

    private final AckTaskQueue ackTaskQueue;

    private final LionServer lionServer;

    private PushListener<IPushMessage> pushListener; //cny_note ？？此部分的MQ实现方式应该没有写完

    private PushTaskExecutor executor; //cny_note 这个线程池负责发送消息给骑手和响应结果给商家两种任务


    public PushCenter(LionServer lionServer) {
        this.lionServer = lionServer;
        this.ackTaskQueue = new AckTaskQueue(lionServer);
    }

    @Override
    public void push(IPushMessage message) {
        if (message.isBroadcast()) { //cny_note 对于广播消息是做局部流控，而对于单发消息是做全局流控
            FlowControl flowControl = (message.getTaskId() == null)//cny_note ？应该是流控策略
                    ? new FastFlowControl(limit, max, duration)//？？这个流控类里count、total为什么没有考虑并发时原子问题 ！！这里商家每条广播消息到达具体分布式节点后，还要通过本地路由发送给骑手，这时候这个流控类实例只对发送给骑手时做流控
                    : new RedisFlowControl(message.getTaskId(), max);
            addTask(new BroadcastPushTask(lionServer, message, flowControl));
        } else {
            addTask(new SingleUserPushTask(lionServer, message, globalFlowControl));
        }
    }

    public void addTask(PushTask task) {
        executor.addTask(task);
        logger.debug("add new task to push center, count={}, task={}", taskNum.incrementAndGet(), task);
    }

    public void delayTask(long delay, PushTask task) {
        executor.delayTask(delay, task);
        logger.debug("delay task to push center, count={}, task={}", taskNum.incrementAndGet(), task);
    }

    @Override
    protected void doStart(Listener listener) throws Throwable {
        this.pushListener = PushListenerFactory.create();
        this.pushListener.init(lionServer);

        if (CC.lion.net.udpGateway() || CC.lion.thread.pool.push_task > 0) {//cny_note 这两者的区别在于后者使用消息所在的netty channel的线程池执行，前
            executor = new CustomJDKExecutor(lionServer.getMonitor().getThreadPoolManager().getPushTaskTimer());
        } else {//实际情况使用EventLoo并没有更快，还有待测试
            executor = new NettyEventLoopExecutor();
        }

        MBeanRegistry.getInstance().register(new PushCenterBean(taskNum), null);//cny_note ？？涉及到JMX知识点，待研究 https://www.sohu.com/a/164585571_355142
        ackTaskQueue.start();
        logger.info("push center start success");
        listener.onSuccess();
    }

    @Override
    protected void doStop(Listener listener) throws Throwable {
        executor.shutdown();
        ackTaskQueue.stop();
        logger.info("push center stop success");
        listener.onSuccess();
    }

    public PushListener<IPushMessage> getPushListener() {
        return pushListener;
    }

    public AckTaskQueue getAckTaskQueue() {
        return ackTaskQueue;
    }

    /**
     * TCP 模式直接使用GatewayServer work 线程池
     */
    private static class NettyEventLoopExecutor implements PushTaskExecutor {

        @Override
        public void shutdown() {
        }

        @Override
        public void addTask(PushTask task) {
            task.getExecutor().execute(task);//？？这个代码感觉不是很符合接口职责单一原则，因为task里面科等用到了消息的信息
        }

        @Override
        public void delayTask(long delay, PushTask task) {
            task.getExecutor().schedule(task, delay, TimeUnit.NANOSECONDS);
        }
    }


    /**
     * UDP 模式使用自定义线程池
     */
    private static class CustomJDKExecutor implements PushTaskExecutor {
        private final ScheduledExecutorService executorService;

        private CustomJDKExecutor(ScheduledExecutorService executorService) {
            this.executorService = executorService;
        }

        @Override
        public void shutdown() {
            executorService.shutdown();
        }

        @Override
        public void addTask(PushTask task) {
            executorService.execute(task);
        }

        @Override
        public void delayTask(long delay, PushTask task) {
            executorService.schedule(task, delay, TimeUnit.NANOSECONDS);
        }
    }

    private interface PushTaskExecutor {

        void shutdown();

        void addTask(PushTask task);

        void delayTask(long delay, PushTask task);
    }
}
