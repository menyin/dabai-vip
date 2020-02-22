
package com.gupao.edu.vip.lion.cache.redis.mq;

import com.gupao.edu.vip.lion.tools.Jsons;
import com.gupao.edu.vip.lion.tools.log.Logs;
import redis.clients.jedis.JedisPubSub;

/**
 * cny_note 继承JedisPubSub后想要接收什么事件就重写什么方法
 */
public final class Subscriber extends JedisPubSub {
    private final ListenerDispatcher listenerDispatcher;//cny_note 应该又是一个事件派发器，所谓的委派者模式

    public Subscriber(ListenerDispatcher listenerDispatcher) {
        this.listenerDispatcher = listenerDispatcher;
    }

    /**
     * 普通订阅消息接收时触发
     * @param channel
     * @param message
     */
    @Override
    public void onMessage(String channel, String message) {
        Logs.CACHE.info("onMessage:{},{}", channel, message);
        listenerDispatcher.onMessage(channel, message);
        super.onMessage(channel, message);//cny_note 父类这些方法都是空实现，为何还要调用一下
    }

    /**
     * 正则订阅消息接收时触发
     * @param pattern
     * @param channel
     * @param message
     */
    @Override
    public void onPMessage(String pattern, String channel, String message) {
        Logs.CACHE.info("onPMessage:{},{},{}", pattern, channel, message);
        super.onPMessage(pattern, channel, message);
    }


    /**
     * 正则消息订阅初始化时触发
     * @param pattern
     * @param subscribedChannels
     */
    @Override
    public void onPSubscribe(String pattern, int subscribedChannels) {
        Logs.CACHE.info("onPSubscribe:{},{}", pattern, subscribedChannels);
        super.onPSubscribe(pattern, subscribedChannels);
    }
    /**
     * 正则消息取消订阅时触发
     * @param pattern
     * @param subscribedChannels
     */
    @Override
    public void onPUnsubscribe(String pattern, int subscribedChannels) {
        Logs.CACHE.info("onPUnsubscribe:{},{}", pattern, subscribedChannels);
        super.onPUnsubscribe(pattern, subscribedChannels);
    }
    /**
     * 普通消息订阅初始化时触发
     * @param channel
     * @param subscribedChannels
     */
    @Override
    public void onSubscribe(String channel, int subscribedChannels) {
        Logs.CACHE.info("onSubscribe:{},{}", channel, subscribedChannels);
        super.onSubscribe(channel, subscribedChannels);
    }
    /**
     * 普通消息取消订阅时触发
     * @param channel
     * @param subscribedChannels
     */
    @Override
    public void onUnsubscribe(String channel, int subscribedChannels) {
        Logs.CACHE.info("onUnsubscribe:{},{}", channel, subscribedChannels);
        super.onUnsubscribe(channel, subscribedChannels);
    }


    @Override
    public void unsubscribe() {
        Logs.CACHE.info("unsubscribe");
        super.unsubscribe();
    }

    @Override
    public void unsubscribe(String... channels) {
        Logs.CACHE.info("unsubscribe:{}", Jsons.toJson(channels));
        super.unsubscribe(channels);
    }

}
