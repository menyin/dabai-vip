
package com.gupao.edu.vip.lion.core.ack;

import com.gupao.edu.vip.lion.api.service.BaseService;
import com.gupao.edu.vip.lion.api.service.Listener;
import com.gupao.edu.vip.lion.core.LionServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * cny_note 原理：当商家推送消息给骑手后存储一个AckTask对象到AckTaskQueue队列中，并且设定超时自动清除。
 *               这样当骑手给商家应答时，则会受限于AckTaskQueue队列，一个个取出并回调给商家。
 *               ？这样算是一种限流或防止并发手段吗，这样做的好处
 */
public final class AckTaskQueue extends BaseService {
    private static final int DEFAULT_TIMEOUT = 3000;

    private final Logger logger = LoggerFactory.getLogger(AckTaskQueue.class);

    private final ConcurrentMap<Integer, AckTask> queue = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduledExecutor;
    private LionServer lionServer;

    public AckTaskQueue(LionServer lionServer) {
        this.lionServer = lionServer;
    }

    /**
     * cny_note 大致过程：当商家推送消息给骑手成功后添加一个AckTask实例到队列，AckTask实例会通过线程池执行等待任务timeout毫秒，
     *                   如果没有骑手没有响应就直接cancel(true)线程停止执行任务，并将该AckTask实例从队列中移除。
     *                   如果骑手有响应，会执行AckTask#onResponse，给商家发送响应消息，并将该AckTask实例从队列中移除。
     * @param task
     * @param timeout
     */
    public void add(AckTask task, int timeout) {
        queue.put(task.getAckMessageId(), task);
        task.setAckTaskQueue(this);
        task.setFuture(scheduledExecutor.schedule(task,//使用 task.getExecutor() 并没更快
                timeout > 0 ? timeout : DEFAULT_TIMEOUT,
                TimeUnit.MILLISECONDS
        ));//cny_note 设置Future，只是为了让task内部通过Future对象知道超时任务执行完了没有

        logger.debug("one ack task add to queue, task={}, timeout={}", task, timeout);
    }

    public AckTask getAndRemove(int sessionId) {
        return queue.remove(sessionId);
    }

    @Override
    protected void doStart(Listener listener) throws Throwable {
        scheduledExecutor = lionServer.getMonitor().getThreadPoolManager().getAckTimer();
        super.doStart(listener);
    }

    @Override
    protected void doStop(Listener listener) throws Throwable {
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        super.doStop(listener);
    }
}
