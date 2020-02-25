
package com.gupao.edu.vip.lion.monitor.service;

import com.gupao.edu.vip.lion.api.spi.common.ExecutorFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * cny_note 线程池统一管理，统一命名规范，参数差异化，使得MonitorService里可以对系统中所有线程池进行监控信息收集
 */
public final class ThreadPoolManager {

    private final ExecutorFactory executorFactory = ExecutorFactory.create();//cny_note executor工厂

    private final Map<String, Executor> pools = new ConcurrentHashMap<>();//cny_note 线程池

    public Executor getRedisExecutor() {
        return pools.computeIfAbsent("mq", s -> executorFactory.get(ExecutorFactory.MQ));
    }

    public Executor getEventBusExecutor() {
        return pools.computeIfAbsent("event-bus", s -> executorFactory.get(ExecutorFactory.EVENT_BUS));
    }

    public ScheduledExecutorService getPushClientTimer() {
        return (ScheduledExecutorService) pools.computeIfAbsent("push-client-timer"
                , s -> executorFactory.get(ExecutorFactory.PUSH_CLIENT));
    }

    public ScheduledExecutorService getPushTaskTimer() {
        return (ScheduledExecutorService) pools.computeIfAbsent("push-task-timer"
                , s -> executorFactory.get(ExecutorFactory.PUSH_TASK));
    }

    public ScheduledExecutorService getAckTimer() {
        return (ScheduledExecutorService) pools.computeIfAbsent("ack-timer"
                , s -> executorFactory.get(ExecutorFactory.ACK_TIMER));
    }

    /**
     * cny_note 手动注册自己的线程池
     * @param name
     * @param executor
     */
    public void register(String name, Executor executor) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(executor);
        pools.put(name, executor);
    }

    public Map<String, Executor> getActivePools() {
        return pools;
    }

}
