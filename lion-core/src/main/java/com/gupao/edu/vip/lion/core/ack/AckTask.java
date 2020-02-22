
package com.gupao.edu.vip.lion.core.ack;

import java.util.concurrent.Future;


public final class AckTask implements Runnable {
    private final int ackMessageId;
    private AckTaskQueue ackTaskQueue;
    private AckCallback callback;
    private Future<?> timeoutFuture;

    public AckTask(int ackMessageId) {
        this.ackMessageId = ackMessageId;
    }

    public static AckTask from(int ackMessageId) {
        return new AckTask(ackMessageId);
    }

    public AckTask setAckTaskQueue(AckTaskQueue ackTaskQueue) {
        this.ackTaskQueue = ackTaskQueue;
        return this;
    }

    public void setFuture(Future<?> future) {
        this.timeoutFuture = future;
    }

    public AckTask setCallback(AckCallback callback) {
        this.callback = callback;
        return this;
    }

    public int getAckMessageId() {
        return ackMessageId;
    }

    /**
     * cny_note 通过中断线程来中断任务
     * @return
     */
    private boolean tryDone() {
        return timeoutFuture.cancel(true); //cny_note  Future#cancel详见CSDN《Java之Future（cancel，iSDone）》
    }

    /**
     * cny_note 当骑手往ConnectionServer发送应答消息时调用该方法，详见AckHandler
     */
    public void onResponse() {
        if (tryDone()) {//cny_note 取消超时处理，否则还会执行超时逻辑，执行超时回调
            callback.onSuccess(this);//cny_note callback#onSuccess是商家等待到骑手响应会执行的回调
            callback = null;
        }
    }

    public void onTimeout() {
        AckTask context = ackTaskQueue.getAndRemove(ackMessageId);
        if (context != null && tryDone()) {
            callback.onTimeout(this); //cny_note callback#onTimeout是商家等待骑手响应超时后会执行的回调
            callback = null;
        }
    }

    @Override
    public String toString() {
        return "{" +
                ", ackMessageId=" + ackMessageId +
                '}';
    }

    @Override
    public void run() {
        onTimeout();
    }
}
