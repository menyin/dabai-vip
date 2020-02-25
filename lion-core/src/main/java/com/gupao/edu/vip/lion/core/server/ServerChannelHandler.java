
package com.gupao.edu.vip.lion.core.server;


import com.gupao.edu.vip.lion.api.connection.Connection;
import com.gupao.edu.vip.lion.api.connection.ConnectionManager;
import com.gupao.edu.vip.lion.api.event.ConnectionCloseEvent;
import com.gupao.edu.vip.lion.api.message.PacketReceiver;
import com.gupao.edu.vip.lion.api.protocol.Command;
import com.gupao.edu.vip.lion.api.protocol.Packet;
import com.gupao.edu.vip.lion.network.netty.connection.NettyConnection;
import com.gupao.edu.vip.lion.tools.common.Profiler;
import com.gupao.edu.vip.lion.tools.config.CC;
import com.gupao.edu.vip.lion.tools.event.EventBus;
import com.gupao.edu.vip.lion.tools.log.Logs;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//@ChannelHandler.Sharable使用此注解，意味这个Handler在多个netty服务都用到，属于共享资源
@ChannelHandler.Sharable
public final class ServerChannelHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerChannelHandler.class);

    private static final long profile_slowly_limit = CC.lion.monitor.profile_slowly_duration.toMillis();

    private final boolean security; //是否启用加密
    private final ConnectionManager connectionManager;
    private final PacketReceiver receiver;

    public ServerChannelHandler(boolean security, ConnectionManager connectionManager, PacketReceiver receiver) {
        this.security = security;
        this.connectionManager = connectionManager;
        this.receiver = receiver;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        //cny_note 处理connection
        Packet packet = (Packet) msg;
        byte cmd = packet.cmd;

        try {
            Profiler.start("time cost on [channel read]: ", packet.toString());
            Connection connection = connectionManager.get(ctx.channel());
            LOGGER.debug("channelRead conn={}, packet={}", ctx.channel(), connection.getSessionContext(), msg);//？？参数不对
            connection.updateLastReadTime();
            receiver.onReceive(packet, connection);//receiver其实是个消息分发器
        } finally {
            Profiler.release();
            if (Profiler.getDuration() > profile_slowly_limit) {
                Logs.PROFILE.info("Read Packet[cmd={}] Slowly: \n{}", Command.toCMD(cmd), Profiler.dump());
            }
            Profiler.reset();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        //cny_note 记录connection异常
        Connection connection = connectionManager.get(ctx.channel());
        Logs.CONN.error("client caught ex, conn={}", connection);
        LOGGER.error("caught an ex, channel={}, conn={}", ctx.channel(), connection, cause);
        ctx.close();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //cny_note 添加connection
        Logs.CONN.info("client connected conn={}", ctx.channel());
        Connection connection = new NettyConnection();//cny_note 这里面的SessionContext是个空实例，等到具体消息处理handler会对其属性进行赋值
        connection.init(ctx.channel(), security);
        connectionManager.add(connection);//cny_note 此处已经把连接暂存起来了
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        //cny_note 移除connection
        Connection connection = connectionManager.removeAndClose(ctx.channel());
        EventBus.post(new ConnectionCloseEvent(connection));//触发连接关闭事件
        Logs.CONN.info("client disconnected conn={}", connection);
    }
}