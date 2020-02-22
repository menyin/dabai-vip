
package com.gupao.edu.vip.lion.cache.redis.mq;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.gupao.edu.vip.lion.api.LionContext;
import com.gupao.edu.vip.lion.api.spi.common.MQClient;
import com.gupao.edu.vip.lion.api.spi.common.MQMessageReceiver;
import com.gupao.edu.vip.lion.cache.redis.manager.RedisManager;
import com.gupao.edu.vip.lion.monitor.service.MonitorService;
import com.gupao.edu.vip.lion.tools.log.Logs;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 *  cny_note 该类有MQ发布订阅功能、redis消息订阅消息处理功能（用线程池处理提升性能）
 *           该类相当于是一个Redis的消息订阅发布工具，算是一个“领域驱动”的设计思想
 */
public final class ListenerDispatcher implements MQClient {

    private final Map<String, List<MQMessageReceiver>> subscribes = Maps.newTreeMap();

    private final Subscriber subscriber;

    private Executor executor;

    @Override
    public void init(LionContext context) {//所有的插件都是通过注入LionContext来获取相关的服务
        executor = ((MonitorService) context.getMonitor()).getThreadPoolManager().getRedisExecutor();//cny_note 有待深究
    }

    public ListenerDispatcher() {
        this.subscriber = new Subscriber(this);//cny_note 这里其实可以将Subscriber定义为内部类，将订阅消息接收又重新转回当前类
    }

    public void onMessage(String channel, String message) {
        List<MQMessageReceiver> listeners = subscribes.get(channel);
        if (listeners == null) {
            Logs.CACHE.info("cannot find listener:{}, {}", channel, message);
            return;
        }

        for (MQMessageReceiver listener : listeners) {
            executor.execute(() -> listener.receive(channel, message));//cny_note 用线程池来执行任务，提高性能
        }
    }
    @Override
    public void subscribe(String channel, MQMessageReceiver listener) {
        subscribes.computeIfAbsent(channel, k -> Lists.newArrayList()).add(listener);//本地订阅redis接收的数据
        RedisManager.I.subscribe(subscriber, channel);//cny_note 订阅线上redis的事件 ？？如果每次执行subscribe会重复订阅怎么办。！这个逻辑个人觉得要改下，否则会重复订阅
        // ？？这个订阅会阻塞主线程怎么办 ！！实时上RedisManager.I.subscribe里面用线程执行任务，以防止阻塞主线程
    }

    @Override
    public void publish(String topic, Object message) {
        RedisManager.I.publish(topic, message);
    }

    //cny_note 此方法其实可以对外隐藏
    public Subscriber getSubscriber() {
        return subscriber;
    }
}
