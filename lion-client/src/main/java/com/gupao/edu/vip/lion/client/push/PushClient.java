
package com.gupao.edu.vip.lion.client.push;

import com.gupao.edu.vip.lion.api.LionContext;
import com.gupao.edu.vip.lion.api.push.PushContext;
import com.gupao.edu.vip.lion.api.push.PushException;
import com.gupao.edu.vip.lion.api.push.PushResult;
import com.gupao.edu.vip.lion.api.push.PushSender;
import com.gupao.edu.vip.lion.api.service.BaseService;
import com.gupao.edu.vip.lion.api.service.Listener;
import com.gupao.edu.vip.lion.api.spi.common.CacheManagerFactory;
import com.gupao.edu.vip.lion.api.spi.common.ServiceDiscoveryFactory;
import com.gupao.edu.vip.lion.client.LionClient;
import com.gupao.edu.vip.lion.client.gateway.connection.GatewayConnectionFactory;
import com.gupao.edu.vip.lion.common.router.CachedRemoteRouterManager;
import com.gupao.edu.vip.lion.common.router.RemoteRouter;

import java.util.Set;
import java.util.concurrent.FutureTask;

/**
 * cny_note 宏观看PushClient是让业务系统调用的，用于向另外一些服务（如GatewayServer）传送数据
 * 如：GatewayServer 负责接收 PushClient里GatewayClient 发送过来的消息，然后到LocalRouterManager 查找用户的 Connection，然后把消息交由其下发
 */
public final class PushClient extends BaseService implements PushSender {

    private LionClient lionClient;

    private PushRequestBus pushRequestBus;

    private CachedRemoteRouterManager cachedRemoteRouterManager;

    private GatewayConnectionFactory gatewayConnectionFactory;



    @Override
    protected void doStart(Listener listener) throws Throwable {//？？这个方法写得感觉有点乱
        if (lionClient == null) {
            lionClient = new LionClient();
        }

        pushRequestBus = lionClient.getPushRequestBus();
        cachedRemoteRouterManager = lionClient.getCachedRemoteRouterManager();
        gatewayConnectionFactory = lionClient.getGatewayConnectionFactory();

        ServiceDiscoveryFactory.create().syncStart();
        CacheManagerFactory.create().init();
        pushRequestBus.syncStart();
        gatewayConnectionFactory.start(listener);
    }

    @Override
    protected void doStop(Listener listener) throws Throwable {
        ServiceDiscoveryFactory.create().syncStop();
        CacheManagerFactory.create().destroy();
        pushRequestBus.syncStop();
        gatewayConnectionFactory.stop(listener);
    }


    /******** cny_note PushSender Methods begin *********/

    @Override
    public FutureTask<PushResult> send(PushContext ctx) {
        if (ctx.isBroadcast()) {
            return send0(ctx.setUserId(null));
        } else if (ctx.getUserId() != null) {
            return send0(ctx);
        } else if (ctx.getUserIds() != null) {
            FutureTask<PushResult> task = null;
            for (String userId : ctx.getUserIds()) {
                task = send0(ctx.setUserId(userId));
            }
            return task;//？？这里显然逻辑不对，因为发送多个用户时，每个task可能对应到不同的分布式服务节点
        } else {
            throw new PushException("param error.");
        }
    }

    /******** cny_note PushSender Methods end *********/



    @Override
    public boolean isRunning() {
        return started.get();
    }

    @Override
    public void setLionContext(LionContext context) {
        this.lionClient = ((LionClient) context);
    }



    private FutureTask<PushResult> send0(PushContext ctx) {
        if (ctx.isBroadcast()) {
            return PushRequest.build(lionClient, ctx).broadcast();
        } else {
            Set<RemoteRouter> remoteRouters = cachedRemoteRouterManager.lookupAll(ctx.getUserId());
            if (remoteRouters == null || remoteRouters.isEmpty()) {
                return PushRequest.build(lionClient, ctx).onOffline();
            }
            FutureTask<PushResult> task = null;
            for (RemoteRouter remoteRouter : remoteRouters) {
                task = PushRequest.build(lionClient, ctx).send(remoteRouter);
            }
            return task;//？？这个逻辑也明显有问题，除非send是按顺序执行的
        }
    }
}
