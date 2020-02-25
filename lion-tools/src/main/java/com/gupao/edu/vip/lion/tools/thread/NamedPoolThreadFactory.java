
package com.gupao.edu.vip.lion.tools.thread;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * cny_note 主要是根据业务类型生产带有业务类型名的线程
 *          通过该工厂生产的线程的名称=【业务类型名+num】
 */
public final class NamedPoolThreadFactory implements ThreadFactory {
    private static final AtomicInteger poolNum = new AtomicInteger(1);

    private final AtomicInteger threadNum = new AtomicInteger(1);

    private final ThreadGroup group;//？？线程组概念有待深究
    private final String namePre;//
    private final boolean isDaemon;//cny_note 是否后台线程

    public NamedPoolThreadFactory(String prefix) {
        this(prefix, true);
    }

    public NamedPoolThreadFactory(String prefix, boolean daemon) {
        SecurityManager manager = System.getSecurityManager();//？？ 安全管理器有待深究,设置这个线程组有何作用
        if (manager != null) {
            group = manager.getThreadGroup();
        } else {
            group = Thread.currentThread().getThreadGroup();
        }
        isDaemon = daemon;
        namePre = prefix + "-p-" + poolNum.getAndIncrement() + "-t-";
    }

    /**
     * stackSize - 新线程的预期堆栈大小，为零时表示忽略该参数
     */
    @Override
    public Thread newThread(Runnable runnable) {
        Thread t = new Thread(group, runnable, namePre + threadNum.getAndIncrement(), 0);
        t.setContextClassLoader(NamedPoolThreadFactory.class.getClassLoader());
        t.setPriority(Thread.NORM_PRIORITY);//默认优先级
        t.setDaemon(isDaemon);//设置后台线程
        return t;
    }

}
