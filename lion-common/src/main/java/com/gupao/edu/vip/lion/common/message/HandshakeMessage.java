
package com.gupao.edu.vip.lion.common.message;

import com.gupao.edu.vip.lion.api.connection.Cipher;
import com.gupao.edu.vip.lion.api.connection.Connection;
import com.gupao.edu.vip.lion.api.protocol.Packet;
import com.gupao.edu.vip.lion.api.spi.core.RsaCipherFactory;
import io.netty.buffer.ByteBuf;

import java.util.Arrays;
import java.util.Map;

import static com.gupao.edu.vip.lion.api.protocol.Command.HANDSHAKE;

/**
 */
public final class HandshakeMessage extends ByteBufMessage {
    public String deviceId;
    public String osName;
    public String osVersion;
    public String clientVersion;
    public byte[] iv;//cny_note AES密钥向量16位
    public byte[] clientKey;//cny_note 客户端随机数16位
    public int minHeartbeat;
    public int maxHeartbeat;
    public long timestamp;

    public HandshakeMessage(Connection connection) {
        super(new Packet(HANDSHAKE, genSessionId()), connection);
    }

    public HandshakeMessage(Packet message, Connection connection) {
        super(message, connection);
    }

    @Override
    public void decode(ByteBuf body) {
        //cny_note ？将各个字段的字节数组复制一份（脱离引用）
        //？？这个解码的过程原理应该是安装body里的字节顺序进行
        deviceId = decodeString(body);
        osName = decodeString(body);
        osVersion = decodeString(body);
        clientVersion = decodeString(body);
        iv = decodeBytes(body);
        clientKey = decodeBytes(body);
        minHeartbeat = decodeInt(body);
        maxHeartbeat = decodeInt(body);
        timestamp = decodeLong(body);
    }

    public void encode(ByteBuf body) {
        encodeString(body, deviceId);
        encodeString(body, osName);
        encodeString(body, osVersion);
        encodeString(body, clientVersion);
        encodeBytes(body, iv);
        encodeBytes(body, clientKey);
        encodeInt(body, minHeartbeat);
        encodeInt(body, maxHeartbeat);
        encodeLong(body, timestamp);
    }

    /**
     * ？？这里为什么只解析了4个属性，那其它属性呢
     * @param body
     */
    @Override
    public void decodeJsonBody(Map<String, Object> body) {
        deviceId = (String) body.get("deviceId");
        osName = (String) body.get("osName");
        osVersion = (String) body.get("osVersion");
        clientVersion = (String) body.get("clientVersion");
    }

    @Override
    protected Cipher getCipher() {
        return RsaCipherFactory.create();//cny_note handshakeMessage消息是客户端和服务端都需要的，此时Rsa加密工具里会包含私钥和公钥属性，但客户端只有公钥属性不为null，而服务端则相反。
    }

    @Override
    public String toString() {
        return "HandshakeMessage{" +
                "clientKey=" + Arrays.toString(clientKey) +
                ", deviceId='" + deviceId + '\'' +
                ", osName='" + osName + '\'' +
                ", osVersion='" + osVersion + '\'' +
                ", clientVersion='" + clientVersion + '\'' +
                ", iv=" + Arrays.toString(iv) +
                ", minHeartbeat=" + minHeartbeat +
                ", maxHeartbeat=" + maxHeartbeat +
                ", timestamp=" + timestamp +
                ", packet=" + packet +
                '}';
    }
}
