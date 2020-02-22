
package com.gupao.edu.vip.lion.api.message;

import com.gupao.edu.vip.lion.api.connection.Connection;
import com.gupao.edu.vip.lion.api.protocol.Packet;
import io.netty.channel.ChannelFutureListener;

/**
 * cny_note  具体类型的消息是由Packet转换过来的，而Message是各种具体消息的抽象，它定义了具体消息需要的行为规范
 *           同时Message实际上是Connection和packet的聚合，所以它又具备了Connection发送消息的能力
 */
public interface Message {

    Connection getConnection();

    void decodeBody();

    void encodeBody();

    /**
     * 发送当前message, 并根据情况最body进行数据压缩、加密
     *
     * @param listener 发送成功后的回调
     */
    void send(ChannelFutureListener listener);

    /**
     * 发送当前message, 不会对body进行数据压缩、加密, 原样发送
     *
     * @param listener 发送成功后的回调
     */
    void sendRaw(ChannelFutureListener listener);

    Packet getPacket();
}
