
package com.gupao.edu.vip.lion.api.protocol;


import com.gupao.edu.vip.lion.api.Constants;
import com.gupao.edu.vip.lion.api.spi.common.Json;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import java.util.Map;


public final class JsonPacket extends Packet {

    public Map<String, Object> body; //？？此处和父类Packet的body类型不一致会有问题吗

    public JsonPacket() {
        super(Command.UNKNOWN);
        this.addFlag(FLAG_JSON_BODY); //cny_note 当添加了此FLAG_JSON_BODY标志的packet包，在解密时候是解密为具体的xxxMessage实例的成员属性
    }

    public JsonPacket(Command cmd) {
        super(cmd);
        this.addFlag(FLAG_JSON_BODY);
    }

    public JsonPacket(Command cmd, int sessionId) {
        super(cmd, sessionId);
        this.addFlag(FLAG_JSON_BODY);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getBody() {
        return body;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> void setBody(T body) {
        this.body = (Map<String, Object>) body;
    }

    @Override
    public int getBodyLength() {
        return body == null ? 0 : body.size();
    }

    @Override
    public Packet response(Command command) {
        return new JsonPacket(command, sessionId);
    }

    @Override
    public Object toFrame(Channel channel) {
        byte[] json = Json.JSON.toJson(this).getBytes(Constants.UTF_8);
        return new TextWebSocketFrame(Unpooled.wrappedBuffer(json));// ？？TextWebSocketFrame有待学习
    }

    @Override
    public String toString() {
        return "JsonPacket{" +
                "cmd=" + cmd +
                ", cc=" + cc +
                ", flags=" + flags +
                ", sessionId=" + sessionId +
                ", lrc=" + lrc +
                ", body=" + body +
                '}';
    }
}
