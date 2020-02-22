
package com.gupao.edu.vip.lion.test.push;

import com.gupao.edu.vip.lion.api.push.*;
import com.gupao.edu.vip.lion.common.qps.FlowControl;
import com.gupao.edu.vip.lion.common.qps.GlobalFlowControl;
import com.gupao.edu.vip.lion.tools.log.Logs;
import org.junit.Test;

import java.time.LocalTime;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/**
 * cny_note 带流控和监视的商家客户端如何推送消息demo
 *          ？商家客户端流控功能，是否集成为SDK
 */
public class PushClientTestMain2 {

    public static void main(String[] args) throws Exception {
        new PushClientTestMain2().testPush();
    }

    /**
     * cny_note 特别注意：在启动testPush之前必须先进行连接，即ConnClientTestMain#testConnClient
     * @throws Exception
     */
    @Test
    public void testPush() throws Exception {
        Logs.init();
        PushSender sender = PushSender.create();//cny_note 启动推送客户端SDK
        sender.start().join();
        Thread.sleep(1000);


        Statistics statistics = new Statistics();
        FlowControl flowControl = new GlobalFlowControl(1000);// qps=1000

        ScheduledThreadPoolExecutor service = new ScheduledThreadPoolExecutor(4);//？？这里为什么不直接用Executors？ 用那种线程池有什么讲究
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {//cny_note 单线程延迟定时器：定时输出连接服务器的流量信息
            System.out.println("time=" + LocalTime.now()//cny_note LocalTime深究下
                    + ", flowControl=" + flowControl.report()
                    + ", statistics=" + statistics
            );
        }, 1, 1, TimeUnit.SECONDS);

        for (int k = 0; k < 1000; k++) {
            for (int i = 0; i < 1; i++) {

                while (service.getQueue().size() > 1000) Thread.sleep(1); // 防止内存溢出

                PushMsg msg = PushMsg.build(MsgType.MESSAGE, "this a first push.");
                msg.setMsgId("msgId_" + i);

                PushContext context = PushContext.build(msg)
                        .setAckModel(AckModel.NO_ACK)
                        .setUserId("user-" + i)
                        .setBroadcast(false)
                        .setTimeout(60000)
                        .setCallback(new PushCallback() {
                            @Override
                            public void onResult(PushResult result) {
                                statistics.add(result.resultCode);//cny_note 返回的结果放进统计计数里
                            }
                        });
                service.execute(new PushTask(sender, context, service, flowControl, statistics));
            }
        }

        LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(30000));//cny_note 待深究 详见CSDN 《Thread.sleep、Object.wait、LockSupport.park 区别》
    }

    private static class PushTask implements Runnable {
        PushSender sender;
        FlowControl flowControl;
        Statistics statistics;
        ScheduledExecutorService executor;
        PushContext context;

        public PushTask(PushSender sender,
                        PushContext context,
                        ScheduledExecutorService executor,
                        FlowControl flowControl,
                        Statistics statistics) {
            this.sender = sender;
            this.context = context;
            this.flowControl = flowControl;
            this.executor = executor;
            this.statistics = statistics;
        }

        @Override
        public void run() {
            if (flowControl.checkQps()) {
                FutureTask<PushResult> future = sender.send(context);
            } else {
                executor.schedule(this, flowControl.getDelay(), TimeUnit.NANOSECONDS); //cny_note 限流策略的实现 ？？但是限流不应该是在服务端去控制吗？
            }
        }
    }

    private static class Statistics {
        final AtomicInteger successNum = new AtomicInteger();
        final AtomicInteger failureNum = new AtomicInteger();
        final AtomicInteger offlineNum = new AtomicInteger();
        final AtomicInteger timeoutNum = new AtomicInteger();
        AtomicInteger[] counters = new AtomicInteger[]{successNum, failureNum, offlineNum, timeoutNum};

        private void add(int code) {
            counters[code - 1].incrementAndGet();
        }

        @Override
        public String toString() {
            return "{" +
                    "successNum=" + successNum +
                    ", offlineNum=" + offlineNum +
                    ", timeoutNum=" + timeoutNum +
                    ", failureNum=" + failureNum +
                    '}';
        }
    }
}