package com.gupao.edu.vip.netty.ch00;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

public class NettyServer  {

    private static final String IP = "127.0.0.1";
    private static final int port = 6666;
    private static final int BIZGROUPSIZE =  Runtime.getRuntime().availableProcessors() * 2;

    private static final int BIZTHREADSIZE = 100;

    private static final EventLoopGroup bossGroup = new NioEventLoopGroup(BIZGROUPSIZE);//主线程组，相当于nio里的mianReactor
    private static final EventLoopGroup workGroup = new NioEventLoopGroup(BIZTHREADSIZE);//工作线程组，相当于nio里的subReactor

    public static void start() throws Exception {

        ServerBootstrap serverBootstrap = initServerBootstrap();


        ChannelFuture channelFuture = serverBootstrap.bind(IP, port).sync();//sync()是使用同步的方式

        channelFuture.channel().closeFuture().sync();//closeFuture()注册关闭监听，sync()是使用同步的方式
        System.out.println("server start");

    }

    private static ServerBootstrap initServerBootstrap() {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap.group(bossGroup,workGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<Channel>() {//？对于连接上的一些处理。如果是handler(...)则是对服务端自己的一些处理。


                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();//ChannelPipeline用于存放ChannelHandler的容器
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE,0,4,0,4));
                        pipeline.addLast(new StringDecoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new StringEncoder(CharsetUtil.UTF_8));
                        pipeline.addLast(new TcpServerHandler());
                    }
                });
        return serverBootstrap;
    }

    protected static void shutdown(){
        workGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }

    public static void main(String[] args) throws Exception {
        System.out.println("启动Server...");
        NettyServer.start();
    }
}
