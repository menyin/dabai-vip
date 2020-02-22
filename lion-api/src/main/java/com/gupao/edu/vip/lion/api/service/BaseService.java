

package com.gupao.edu.vip.lion.api.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;


public abstract class BaseService implements Service {

    protected final AtomicBoolean started = new AtomicBoolean();//cny_note 默认值为false

    @Override
    public void init() {
    }

    @Override
    public boolean isRunning() {
        return started.get();
    }
    //cny_note ***服务的start异步逻辑看ZKClient即可，但个人觉得这个设计有点混乱
    protected void tryStart(Listener l, FunctionEx function) {
        FutureListener listener = wrap(l);
        if (started.compareAndSet(false, true)) {//？？此处应该是要等listener.monitor()里的任务执行完才更新为true吧
            try {
                init();//相关初始化操作
                //cny_note 这个有点过度设计，其实执行的是具体BaseService子类的doStart方法，如ZKClient#doStart,
                // 而toStart方法里的最后又会调用包装后的listener.onSuccess，onSuccess里会执行complete去触发CompletableFuture类的完成事件
                // 注意ZKClient#doStart里会先执行完一些阻塞的耗时的操作后，然后再执行listener.onSuccess，里面会执行complete去触发CompletableFuture类的完成事件
                // function.apply(listener)执行的就是上述逻辑，
                function.apply(listener); //cny_note 这句代码其实是CompletableFuture实例实现promise的应用。
                listener.monitor(this);//cny_note 这句主要用CompletableFuture实例的线程执行了一个监控任务，用于超时未完成服务启动则直接抛异常，并调用listener.onFailure()
            } catch (Throwable e) {
                listener.onFailure(e);
                throw new ServiceException(e);//都已经执行了失败回调，为何还抛出异常
            }
        } else {
            if (throwIfStarted()) {
                listener.onFailure(new ServiceException("service already started."));
            } else {
                listener.onSuccess();
            }
        }
    }

    protected void tryStop(Listener l, FunctionEx function) {
        FutureListener listener = wrap(l);
        if (started.compareAndSet(true, false)) {
            try {
                function.apply(listener);//？？感觉这个有点过度设计
                listener.monitor(this);//主要用于异步，否则应该放置在function.apply(listener)之前
            } catch (Throwable e) {
                listener.onFailure(e);
                throw new ServiceException(e);
            }
        } else {
            if (throwIfStopped()) {
                listener.onFailure(new ServiceException("service already stopped."));
            } else {
                listener.onSuccess();
            }
        }
    }

    public final CompletableFuture<Boolean> start() {
        FutureListener listener = new FutureListener(started);
        start(listener);//cny_note 这里调用的是具体子类的start，如ZKClient#start(Listener listener)
        return listener;
    }

    /**
     * ？？为什么这里要用 new FutureListener
     * @return
     */
    public final CompletableFuture<Boolean> stop() {
        FutureListener listener = new FutureListener(started);
        stop(listener);
        return listener;
    }

    @Override
    public final boolean syncStart() {
        return start().join();//这里start()返回的是一个FutureListener实例，它继承了CompletableFuture
    }

    @Override
    public final boolean syncStop() {
        return stop().join();
    }

    /**
     * cny_note 注意在NettyTCPServer重写了这个方法的实现，
     *   所以NettyTCPServer子类，如ConnectionServer就不是使用这里的实现逻辑
     *   但ZKClient直接继承BaseService，所以是使用这里的逻辑
     * @param listener 是一个公共的监听事件接口（或者叫C#里的委托）
     */
    @Override
    public void start(Listener listener) {
        tryStart(listener, this::doStart);//cny_note 这里是子类的的doStart，如ZKClient#doStart,子类的doStart方法里可能会是一些耗时的启动操作
    }

    @Override
    public void stop(Listener listener) {
        tryStop(listener, this::doStop);
    }

    protected void doStart(Listener listener) throws Throwable {
        listener.onSuccess();
    }

    protected void doStop(Listener listener) throws Throwable {
        listener.onSuccess();
    }

    /**
     * 控制当服务已经启动后，重复调用start方法，是否抛出服务已经启动异常
     * 默认是true
     *
     * @return true:抛出异常
     */
    protected boolean throwIfStarted() {
        return true;
    }

    /**
     * 控制当服务已经停止后，重复调用stop方法，是否抛出服务已经停止异常
     * 默认是true
     *
     * @return true:抛出异常
     */
    protected boolean throwIfStopped() {
        return true;
    }

    /**
     * 服务启动停止，超时时间, 默认是10s
     *
     * @return 超时时间
     */
    protected int timeoutMillis() {
        return 1000 * 10;
    }

    protected interface FunctionEx {
        void apply(Listener l) throws Throwable;
    }

    /**
     * 防止Listener被重复执行
     * 如果 l 本身就是FutureListener就强转，否则用FutureListener包装下
     * @param l listener
     * @return FutureListener
     */
    public FutureListener wrap(Listener l) {
        if (l == null) return new FutureListener(started);
        if (l instanceof FutureListener) return (FutureListener) l;
        return new FutureListener(l, started);
    }
}
