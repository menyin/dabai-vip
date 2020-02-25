package com.gupao.edu.vip.lion.api.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ？？FutureListener扩展CompletableFuture的具体意义是什么
 * cny_note 此类是Listener的包装类
 *          扩展功能：
 *           使用该包装类可以做promise规范
 *           使用该包装类可以做服务操作过程中超时引发异步回调
 *
 */
public class FutureListener extends CompletableFuture<Boolean> implements Listener {
    private final Listener listener;
    private final AtomicBoolean started;

    public FutureListener(AtomicBoolean started) {
        this.started = started;
        this.listener = null;
    }

    public FutureListener(Listener listener, AtomicBoolean started) {
        this.listener = listener;
        this.started = started;
    }

    @Override
    public void onSuccess(Object... args) {
        if (isDone()) return;// 防止Listener被重复执行 cny_note 当complete()已经执行过了isDone()则为true，为此判断isDone()知道complete()是否已经执行过，防止事件重复触发
        complete(started.get());//cny_note CompletableFuture#complete用于通知CompletableFuture的实例对应的任务线程直接完成任务。从而触发任务完成后续的事件
        if (listener != null) listener.onSuccess(args);
    }

    @Override
    public void onFailure(Throwable cause) {
        if (isDone()) return;// 防止Listener被重复执行
        completeExceptionally(cause);
        if (listener != null) listener.onFailure(cause);
        throw cause instanceof ServiceException
                ? (ServiceException) cause
                : new ServiceException(cause);
    }

    /**
     * 防止服务长时间卡在某个地方，增加超时监控
     *
     * @param service 服务
     */
    public void monitor(BaseService service) {
        if (isDone()) return;// 防止Listener被重复执行
        runAsync(() -> {//？？问题是启动过程是同步的或者说是阻塞的，而失败回调却弄成是异步的，这里似乎没有什么意义
            try {
                this.get(service.timeoutMillis(), TimeUnit.MILLISECONDS);//cny_note 在超时时间内如果没返回就抛异常 这里已经是在等待started置为true了，其实没做什么事情
            } catch (Exception e) {
                this.onFailure(new ServiceException(String.format("service %s monitor timeout", service.getClass().getSimpleName())));//在指定时间内没返回结果则执行错误回调
            }
        });
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

}