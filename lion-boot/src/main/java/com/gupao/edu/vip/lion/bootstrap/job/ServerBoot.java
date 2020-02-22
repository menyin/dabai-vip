
package com.gupao.edu.vip.lion.bootstrap.job;

import com.gupao.edu.vip.lion.api.service.Listener;
import com.gupao.edu.vip.lion.api.service.Server;
import com.gupao.edu.vip.lion.api.spi.common.ServiceRegistryFactory;
import com.gupao.edu.vip.lion.api.srd.ServiceNode;
import com.gupao.edu.vip.lion.tools.log.Logs;

/**
 * ServerBoot是服务端相关服务的启动类
 * 统一了所有服务启动后注册到注册中心上（统一在服务启动的成功回调里处理）
 *
 */
public final class ServerBoot extends BootJob {
    private final Server server;
    private final ServiceNode node;

    public ServerBoot(Server server, ServiceNode node) {
        this.server = server;
        this.node = node;//cny_note 发现node只是为了服务启动在注册中心注册时用到，那不用讲node传递到server内部供调用吗？
    }

    @Override
    public void start() {
        server.init();
        server.start(new Listener() {//cny_note 这里new的不是一个FutureListener或者Completable接口实例，而只是一个简单的Listener接口作为回调函数.
            @Override
            public void onSuccess(Object... args) {
                Logs.Console.info("start {} success on:{}", server.getClass().getSimpleName(), args[0]);
                if (node != null) {//注册应用到zk
                    ServiceRegistryFactory.create().register(node);
                    Logs.RSD.info("register {} to srd success.", node);
                }
                startNext();// cny_note 调用链下级调用放在当前服务启动成功回调里
            }

            @Override
            public void onFailure(Throwable cause) {
                Logs.Console.error("start {} failure, jvm exit with code -1", server.getClass().getSimpleName(), cause);
                System.exit(-1);
            }
        });
    }

    @Override
    protected void stop() {
        stopNext();
        if (node != null) {
            ServiceRegistryFactory.create().deregister(node);
        }
        Logs.Console.info("try shutdown {}...", server.getClass().getSimpleName());
        server.stop().join();//？？此处的join()方法有待深究
        Logs.Console.info("{} shutdown success.", server.getClass().getSimpleName());
    }

    @Override
    protected String getName() {
        return super.getName() + '(' + server.getClass().getSimpleName() + ')';
    }
}
