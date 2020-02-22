
package com.gupao.edu.vip.lion.common.router;

import com.gupao.edu.vip.lion.api.router.ClientLocation;
import com.gupao.edu.vip.lion.api.router.Router;

/**
 * cny_note 存储了骑手用户所在的连接服务器节点的ip+port等信息，并且会一个userId为key存储在redis的Hash表里，因为还有deviceId。一个用户会对应多个设备并可以连接到不同的服务器
 *          事实上ClientLocation已经包含了远程路由的信息，而RemoteRouter只是对其进行包装以适应Router接口的格式
 */
public final class RemoteRouter implements Router<ClientLocation> {
    private final ClientLocation clientLocation;

    public RemoteRouter(ClientLocation clientLocation) {
        this.clientLocation = clientLocation;
    }

    public boolean isOnline(){
        return clientLocation.isOnline();
    }

    public boolean isOffline(){
        return clientLocation.isOffline();
    }

    @Override
    public ClientLocation getRouteValue() {
        return clientLocation;
    }

    @Override
    public RouterType getRouteType() {
        return RouterType.REMOTE;
    }

    @Override
    public String toString() {
        return "RemoteRouter{" + clientLocation + '}';
    }
}
