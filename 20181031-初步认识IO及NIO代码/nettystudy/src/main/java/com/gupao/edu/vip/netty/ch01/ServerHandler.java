package com.gupao.edu.vip.netty.ch01;

import io.netty.channel.*;

import java.net.InetAddress;
import java.util.Date;

@ChannelHandler.Sharable
public class ServerHandler extends SimpleChannelInboundHandler<String> {
    /**
     * - 建⽴立连接时，发送⼀一条庆祝消息
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws
            Exception {
     // 为新连接发送庆祝
        ctx.write("Welcome to " +
                InetAddress.getLocalHost().getHostName() + "!/r/n");
        ctx.write("It is " + new Date() + " now./r/n");
        ctx.flush();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        // Generate and write a response.
        String response;
        boolean close = false;
        if (msg == null || msg .equals("")) {
            response = "Please type something./r/n";
        } else if ("bye".equals(msg)) {
            response = "Have a good day!/r/n";
            close = true;
        } else {
            response = "Did you say '" + msg + "'?/r/n";
        }
        ChannelFuture future = ctx.write(response);
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    //业务逻辑处理理
    @Override
    public void channelRead0(ChannelHandlerContext ctx, String request)
            throws Exception {
       channelRead(ctx,request);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

     //异常处理理
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable
            cause) {
        cause.printStackTrace();
        ctx.close();
    }
}