
package com.gupao.edu.vip.lion.client.gateway.connection;

import com.google.common.collect.Maps;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.gupao.edu.vip.lion.api.connection.Connection;
import com.gupao.edu.vip.lion.api.event.ConnectionConnectEvent;
import com.gupao.edu.vip.lion.api.service.Listener;
import com.gupao.edu.vip.lion.api.spi.common.ServiceDiscoveryFactory;
import com.gupao.edu.vip.lion.api.srd.ServiceDiscovery;
import com.gupao.edu.vip.lion.api.srd.ServiceNode;
import com.gupao.edu.vip.lion.client.LionClient;
import com.gupao.edu.vip.lion.client.gateway.GatewayClient;
import com.gupao.edu.vip.lion.common.message.BaseMessage;
import com.gupao.edu.vip.lion.tools.event.EventBus;
import io.netty.channel.ChannelFuture;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.gupao.edu.vip.lion.api.srd.ServiceNames.GATEWAY_SERVER;
import static com.gupao.edu.vip.lion.tools.config.CC.lion.net.gateway_client_num;

/**
 * cny_note 在商家启动SDK中LionClient时，通过一系列调用初始化GatewayTCPConnectionFactory实例
 *          GatewayTCPConnectionFactory实例启动会到注册中心查找可用的所有连接服务器节点（也是服务端网关服务器），进行预连接产生connection，并监控节点变化以更新connection列表
 */
public class GatewayTCPConnectionFactory extends GatewayConnectionFactory {
    private final AttributeKey<String> attrKey = AttributeKey.valueOf("host_port");//cny_note netty中用于存储业务数据并与通道绑定，可使用ctx.channel().attr(AttributeKey.valueOf(key))获取
    private final Map<String, List<Connection>> connections = Maps.newConcurrentMap();
    //？？为何还要单独一份连接缓存，ConnectionManager不是有？ ！因为对于同一个服务节点这里建立了多个连接，ConnectionManager仅存了连接，没有存储对应关系，要查找会比较麻烦

    private ServiceDiscovery discovery;
    private GatewayClient gatewayClient;

    private LionClient lionClient;

    public GatewayTCPConnectionFactory(LionClient lionClient) {
        this.lionClient = lionClient;
    }

    @Override
    protected void doStart(Listener listener) throws Throwable {
        EventBus.register(this);

        gatewayClient = new GatewayClient(lionClient);
        gatewayClient.start().join();
        discovery = ServiceDiscoveryFactory.create();
        discovery.subscribe(GATEWAY_SERVER, this);
        discovery.lookup(GATEWAY_SERVER).forEach(this::syncAddConnection);
        listener.onSuccess();
    }
    /*******cny_note zk 上的服务节点发生变更触发的事件 begin **********/
    @Override
    public void onServiceAdded(String path, ServiceNode node) {
        asyncAddConnection(node);
    }

    @Override
    public void onServiceUpdated(String path, ServiceNode node) {
        removeClient(node);
        asyncAddConnection(node);
    }

    @Override
    public void onServiceRemoved(String path, ServiceNode node) {
        removeClient(node);
        //？？这里只清除了GatewayTCPConnectionFactory#connections的连接，但是ConnectionManager里的连接没有清除
        //！！服务端或客户端关闭connection都会触发netty的handler的channelInactive，此处ConnectionManager里的连接是在channelInactive里清除的
        logger.warn("Gateway Server zkNode={} was removed.", node);
    }
    /*******cny_note zk 上的服务节点发生变更触发的事件 end **********/

    @Override
    public void doStop(Listener listener) throws Throwable {
        connections.values().forEach(l -> l.forEach(Connection::close));
        if (gatewayClient != null) {
            gatewayClient.stop().join();
        }
        discovery.unsubscribe(GATEWAY_SERVER, this);
    }

    @Override
    public Connection getConnection(String hostAndPort) {
        List<Connection> connections = this.connections.get(hostAndPort);
        if (connections == null || connections.isEmpty()) {//如果为空, 查询下zk, 做一次补偿, 防止zk丢消息
            synchronized (hostAndPort.intern()) {//同一个host要同步执行, 防止创建很多链接;一定要调用intern
                connections = this.connections.get(hostAndPort);
                if (connections == null || connections.isEmpty()) {//二次检查
                    discovery.lookup(GATEWAY_SERVER)
                            .stream()
                            .filter(n -> hostAndPort.equals(n.hostAndPort()))
                            .forEach(this::syncAddConnection);//cny_note 客户端SDK和服务端节点网关建立连接，注意这里建立连接成功后是在netty的channelHandler#channelActive用EventBus#post，然后此类#on接口。并且整个过程是同步的（synchronized）
                    if (connections == null || connections.isEmpty()) {//如果还是没有链接, 就直接返null失败
                        return null;
                    }
                }
            }
        }

        int L = connections.size();

        Connection connection;
        if (L == 1) {
            connection = connections.get(0);
        } else {//cny_note ？？理论上上面discovery.filter已经进行了hostAndPort过滤，怎么可能出现两个连接
            connection = connections.get((int) (Math.random() * L % L));//cny_note 计算出L以内的随机数 其实直接(int)(Math.random() * L)即可
        }

        if (connection.isConnected()) {
            return connection;
        }

        reconnect(connection, hostAndPort);
        return getConnection(hostAndPort);
    }

    /**
     * cny_note 单发消息
     * @param hostAndPort
     * @param creator
     * @param sender
     * @param <M>
     * @return
     */
    @Override
    public <M extends BaseMessage> boolean send(String hostAndPort, Function<Connection, M> creator, Consumer<M> sender) {
        Connection connection = getConnection(hostAndPort);
        if (connection == null) return false;// gateway server 找不到，直接返回推送失败

        sender.accept(creator.apply(connection));
        return true;
    }

    /**
     * cny_note 广播消息
     * @param creator
     * @param sender
     * @param <M>
     * @return
     */
    @Override
    public <M extends BaseMessage> boolean broadcast(Function<Connection, M> creator, Consumer<M> sender) {
        if (connections.isEmpty()) return false;
        connections
                .values()
                .stream()
                .filter(connections -> connections.size() > 0)
                .forEach(connections -> sender.accept(creator.apply(connections.get(0))));//cny_note 对于同一个服务节点的连接，只取首个
        return true;
    }

    private void reconnect(Connection connection, String hostAndPort) {
        HostAndPort h_p = HostAndPort.fromString(hostAndPort);//cny_note 将形如：192.168.1.10:8080的字符串解析成HostAndPort对象
        connections.get(hostAndPort).remove(connection);
        connection.close();
        addConnection(h_p.getHost(), h_p.getPort(), false);
    }

    private void removeClient(ServiceNode node) {
        if (node != null) {
            List<Connection> clients = connections.remove(getHostAndPort(node.getHost(), node.getPort()));
            if (clients != null) {
                clients.forEach(Connection::close);
            }
        }
    }

    /**
     * cny_note 异步建立连接
     * @param node
     */
    private void asyncAddConnection(ServiceNode node) {
        for (int i = 0; i < gateway_client_num; i++) {// cny_note gateway_client_num网关客户端连接数,多建立一些连接应该可以作为“池”使用
            addConnection(node.getHost(), node.getPort(), false);
        }
    }


    private void syncAddConnection(ServiceNode node) {
        for (int i = 0; i < gateway_client_num; i++) {
            addConnection(node.getHost(), node.getPort(), true);
        }
    }

    /**
     * cny_note 客户端网关和服务网关建立连接，包含几个重要动作
     *          1.建立连接
     *          2.通过EvnetBus将建立的连接缓存到当前类的connections中
     *
     * @param host
     * @param port
     * @param sync 是否同步建立连接
     */
    private void addConnection(String host, int port, boolean sync) {
        ChannelFuture future = gatewayClient.connect(host, port);
        future.channel().attr(attrKey).set(getHostAndPort(host, port));//cny_note 在具体的netty通道handler#channelActive里，通过EventBus将参数又传递到当前类#on方法
        future.addListener(f -> {
            if (!f.isSuccess()) {
                logger.error("create gateway connection failure, host={}, port={}", host, port, f.cause());
            }
        });
        if (sync) future.awaitUninterruptibly();//cny_note 一直阻塞等待io操作完成（即连接建立完成）或抛异常
    }

    @Subscribe
    @AllowConcurrentEvents
    void on(ConnectionConnectEvent event) {
        Connection connection = event.connection;
        String hostAndPort = connection.getChannel().attr(attrKey).getAndSet(null);
        if (hostAndPort == null) {
            InetSocketAddress address = (InetSocketAddress) connection.getChannel().remoteAddress();
            hostAndPort = getHostAndPort(address.getAddress().getHostAddress(), address.getPort());
        }
        connections.computeIfAbsent(hostAndPort, key -> new ArrayList<>(gateway_client_num)).add(connection);//cny_note 将建立的连接缓存在当前类
        logger.info("one gateway client connect success, hostAndPort={}, conn={}", hostAndPort, connection);
    }

    private static String getHostAndPort(String host, int port) {
        return host + ":" + port;
    }

    public GatewayClient getGatewayClient() {
        return gatewayClient;
    }
}
