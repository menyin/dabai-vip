
package com.gupao.edu.vip.lion.monitor.data;

import com.gupao.edu.vip.lion.monitor.quota.impl.*;
import com.gupao.edu.vip.lion.monitor.service.ThreadPoolManager;

/**
 * cny_note 各种jvm相关的信息的收集器
 */
public class ResultCollector {
    private final JVMInfo jvmInfo;
    private final JVMGC jvmgc;
    private final JVMMemory jvmMemory;
    private final JVMThread jvmThread;
    private final JVMThreadPool jvmThreadPool;

    public ResultCollector(ThreadPoolManager threadPoolManager) {
        this.jvmInfo = new JVMInfo();//cny_note jvminfo监控
        this.jvmgc = new JVMGC();//cny_note GC监控
        this.jvmMemory = new JVMMemory();//cny_note 内存监控
        this.jvmThread = new JVMThread();//cny_note 线程监控
        this.jvmThreadPool = new JVMThreadPool(threadPoolManager);//cny_note 线程池监控
    }

    public MonitorResult collect() {
        MonitorResult result = new MonitorResult();
        result.addResult("jvm-info", jvmInfo.monitor());
        result.addResult("jvm-gc", jvmgc.monitor());
        result.addResult("jvm-memory", jvmMemory.monitor());
        result.addResult("jvm-thread", jvmThread.monitor());
        result.addResult("jvm-thread-pool", jvmThreadPool.monitor());
        return result;
    }

    public JVMInfo getJvmInfo() {
        return jvmInfo;
    }

    public JVMGC getJvmgc() {
        return jvmgc;
    }

    public JVMMemory getJvmMemory() {
        return jvmMemory;
    }

    public JVMThread getJvmThread() {
        return jvmThread;
    }

    public JVMThreadPool getJvmThreadPool() {
        return jvmThreadPool;
    }
}
