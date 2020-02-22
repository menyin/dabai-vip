
package com.gupao.edu.vip.lion.api.service;

import java.util.concurrent.CompletableFuture;

/***
 * 无论netty的服务端还是客户端应该都有实现这个接口
 */
public interface Service {
    /**
     * 开始一个服务
     * @param listener 是一个公共的监听事件接口（或者叫C#里的委托）
     */
    void start(Listener listener);
    /**
     * 停止一个服务
     * @param listener 是一个公共的监听事件接口（或者叫C#里的委托）
     */
    void stop(Listener listener);

    CompletableFuture<Boolean> start();//cny_note 无论是同步或异步都可以用

    CompletableFuture<Boolean> stop();//cny_note 无论是同步或异步都可以用

    boolean syncStart();//cny_note 同步启动

    boolean syncStop();//cny_note 同步停止

    void init();

    boolean isRunning();

}
