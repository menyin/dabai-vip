
package com.gupao.edu.vip.lion.tools.thread.pool;

import java.util.concurrent.*;

/**
 * cny_note 线程池工厂,分门别类只为了监控模块对线程池进行监控
 */
public final class DefaultExecutor extends ThreadPoolExecutor {

    public DefaultExecutor(int corePoolSize, int maximumPoolSize,
                           long keepAliveTime, TimeUnit unit,
                           BlockingQueue<Runnable> workQueue,
                           ThreadFactory threadFactory,
                           RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

}
