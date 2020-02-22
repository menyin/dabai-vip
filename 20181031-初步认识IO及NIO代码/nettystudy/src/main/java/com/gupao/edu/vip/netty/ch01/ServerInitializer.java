package com.gupao.edu.vip.netty.ch01;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class ServerInitializer extends ChannelInitializer<SocketChannel> {
    private static final StringDecoder DECODER = new StringDecoder();
    private static final StringEncoder ENCODER = new StringEncoder();
    private static final ServerHandler SERVER_HANDLER = new ServerHandler();

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        // 添加帧限定符来防⽌止粘包现象
        pipeline.addLast(new DelimiterBasedFrameDecoder(8192,
                Delimiters.lineDelimiter()));
       // 解码和编码，应和客户端⼀一致
        pipeline.addLast(DECODER);
      // 业务逻辑实现类
        pipeline.addLast(SERVER_HANDLER);
    }
}