
package com.gupao.edu.vip.lion.core.router;

import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.gupao.edu.vip.lion.api.event.UserOfflineEvent;
import com.gupao.edu.vip.lion.api.event.UserOnlineEvent;
import com.gupao.edu.vip.lion.api.spi.common.MQClient;
import com.gupao.edu.vip.lion.api.spi.common.MQClientFactory;
import com.gupao.edu.vip.lion.common.router.RemoteRouterManager;
import com.gupao.edu.vip.lion.common.user.UserManager;
import com.gupao.edu.vip.lion.tools.event.EventConsumer;

import static com.gupao.edu.vip.lion.api.event.Topics.OFFLINE_CHANNEL;
import static com.gupao.edu.vip.lion.api.event.Topics.ONLINE_CHANNEL;

/**
 * cny_note 在EventBus上注册本地的用户上下线监听（分布式中某一个ConnectionServer节点）
 *          通过MQ发布订阅来动态感知用户上下线，新的实现好像是直接通过查询骑手连接所在的网关，通过网关进行上线下用户更新
 *
 */
public final class UserEventConsumer extends EventConsumer {

    private final MQClient mqClient = MQClientFactory.create();//cny_note 这里是ListenerDispatcher实例

    private final UserManager userManager; //cny_note 全部的在线列表是存在线上redis，所以userManager是用于管理线上用户列表的

    public UserEventConsumer(RemoteRouterManager remoteRouterManager) {
        this.userManager = new UserManager(remoteRouterManager);
    }

    @Subscribe
    @AllowConcurrentEvents
    void on(UserOnlineEvent event) {
        userManager.addToOnlineList(event.getUserId());//cny_note 更新线上用户列表
        mqClient.publish(ONLINE_CHANNEL, event.getUserId());//cny_note 推送事件到MQ上 ？？这里不应该是要连带设备类型一起带过去吗，否则怎么晓得是哪种类型设备下线。！因为在线用户列表之存储了userId
    }

    @Subscribe
    @AllowConcurrentEvents
    void on(UserOfflineEvent event) {
        userManager.remFormOnlineList(event.getUserId());
        mqClient.publish(OFFLINE_CHANNEL, event.getUserId());
    }
    //cny_note如果要对外暴露，则其实userManager可以设置为单例
    public UserManager getUserManager() {
        return userManager;
    }
}
