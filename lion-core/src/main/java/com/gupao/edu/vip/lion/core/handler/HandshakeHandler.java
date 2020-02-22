
package com.gupao.edu.vip.lion.core.handler;

import com.google.common.base.Strings;
import com.gupao.edu.vip.lion.api.connection.Connection;
import com.gupao.edu.vip.lion.api.connection.SessionContext;
import com.gupao.edu.vip.lion.api.protocol.Packet;
import com.gupao.edu.vip.lion.common.handler.BaseMessageHandler;
import com.gupao.edu.vip.lion.common.message.ErrorMessage;
import com.gupao.edu.vip.lion.common.message.HandshakeMessage;
import com.gupao.edu.vip.lion.common.message.HandshakeOkMessage;
import com.gupao.edu.vip.lion.common.security.AesCipher;
import com.gupao.edu.vip.lion.common.security.CipherBox;
import com.gupao.edu.vip.lion.core.LionServer;
import com.gupao.edu.vip.lion.core.session.ReusableSession;
import com.gupao.edu.vip.lion.core.session.ReusableSessionManager;
import com.gupao.edu.vip.lion.tools.config.ConfigTools;
import com.gupao.edu.vip.lion.tools.log.Logs;

import static com.gupao.edu.vip.lion.common.ErrorCode.REPEAT_HANDSHAKE;

/**
 * cny_note 握手消息的handler
 */
public final class HandshakeHandler extends BaseMessageHandler<HandshakeMessage> {

    private final ReusableSessionManager reusableSessionManager;

    public HandshakeHandler(LionServer lionServer) {
        this.reusableSessionManager = lionServer.getReusableSessionManager();
    }

    @Override
    public HandshakeMessage decode(Packet packet, Connection connection) {
        return new HandshakeMessage(packet, connection);
    }

    @Override
    public void handle(HandshakeMessage message) {
        if (message.getConnection().getSessionContext().isSecurity()) {// cny_note 在ServerChannelHandler#channelActive里设置是否加密（安全）
            doSecurity(message);
        } else {
            doInsecurity(message);
        }
    }

    /**
     * **cny_note 大白文档中描述的密钥交换过程，第一步公钥加密R1其实是加密整个消息的body。而公钥加密的算法是用getCipher()得到
     * @param message
     */
    private void doSecurity(HandshakeMessage message) {
        byte[] iv = message.iv;//AES密钥向量16位 cny_note 相当于加盐，并且这个盐前后端都是一致的
        byte[] clientKey = message.clientKey;//客户端随机数16位 //cny_note 是经过服务端私钥解密过的，也就是文档中描述的客户端随机数R1 ？？websocket客户端js代码并未找到发送了什么随机数 ！在ConnClientTestMain的ClientConfig
        byte[] serverKey = CipherBox.I.randomAESKey();//服务端随机数16位 //cny_note 也就是文档中描述的客户端随机数 R2
        byte[] sessionKey = CipherBox.I.mixKey(clientKey, serverKey);//会话密钥16位

        //1.校验客户端消息字段
        if (Strings.isNullOrEmpty(message.deviceId)
                || iv.length != CipherBox.I.getAesKeyLength()
                || clientKey.length != CipherBox.I.getAesKeyLength()) {
            ErrorMessage.from(message).setReason("Param invalid").close();
            Logs.CONN.error("handshake failure, message={}, conn={}", message, message.getConnection());
            return;
        }

        //2.重复握手判断
        SessionContext context = message.getConnection().getSessionContext();
        if (message.deviceId.equals(context.deviceId)) { // ？？如果这边不相等，则context.deviceId应该在握手成功后（即成功发送给骑手客户端后）会进行赋值，而除了握手消息以外的消息是不会再发deviceId
            ErrorMessage.from(message).setErrorCode(REPEAT_HANDSHAKE).send();
            Logs.CONN.warn("handshake failure, repeat handshake, conn={}", message.getConnection());
            return;
        }

        //3.更换会话密钥RSA=>AES(clientKey)
        context.changeCipher(new AesCipher(clientKey, iv));// cny_note 这里只是设置了Aes的加密器

        //4.生成可复用session, 用于快速重连 //cny_note 就是存储客户端信息以便下次不用握手直接链接，模拟session功能
        ReusableSession session = reusableSessionManager.genSession(context);

        //5.计算心跳时间
        int heartbeat = ConfigTools.getHeartbeat(message.minHeartbeat, message.maxHeartbeat);//？？为什么是客户端发送消息携带着最大最小心跳时间

        //6.响应握手成功消息
        HandshakeOkMessage
                .from(message) //cny_note 通过请求消息message去构造一个响应消息，from里会将构造的HandshakeOkMessage.packet.sessionId指向message.packet.sessionId ？？但是此时message.packet.sessionId 应该是null
                .setServerKey(serverKey) //cny_note serverKey是R2 即服务端的随机数 ？？可是这里并不像大白所说是加密AES（R1+R2）后的数据发送给骑手客户端
                .setHeartbeat(heartbeat)
                .setSessionId(session.sessionId) //cny_note sessionId赋值进去
                .setExpireTime(session.expireTime)
                .send(f -> {
                            //cny_note 服务端发送握手成功消息给客户端后存储session
                            if (f.isSuccess()) { //如果成功发送给骑手客户端，则握手成功，那就可以更新服务端的connection.context内容了
                                //7.更换会话密钥AES(clientKey)=>AES(sessionKey)
                                context.changeCipher(new AesCipher(sessionKey, iv));//cny_note 原本保存的Rsa加密器是由客户发送过来消息携带的。
                                //8.保存client信息到当前连接
                                context.setOsName(message.osName)
                                        .setOsVersion(message.osVersion)
                                        .setClientVersion(message.clientVersion)
                                        .setDeviceId(message.deviceId)
                                        .setHeartbeat(heartbeat);

                                //9.保存可复用session到Redis, 用于快速重连
                                reusableSessionManager.cacheSession(session);

                                Logs.CONN.info("handshake success, conn={}", message.getConnection());
                            } else {
                                Logs.CONN.info("handshake failure, conn={}", message.getConnection(), f.cause());//cny_note f.cause()是获取异常
                            }
                        }
                );
    }

    /**
     * cny_note 非安全连接没有校验重复握手、没有存储session以便快速重连
     * @param message
     */
    private void doInsecurity(HandshakeMessage message) {

        //1.校验客户端消息字段
        if (Strings.isNullOrEmpty(message.deviceId)) {
            ErrorMessage.from(message).setReason("Param invalid").close();
            Logs.CONN.error("handshake failure, message={}, conn={}", message, message.getConnection());
            return;
        }

        //2.重复握手判断
        SessionContext context = message.getConnection().getSessionContext();
        if (message.deviceId.equals(context.deviceId)) {
            ErrorMessage.from(message).setErrorCode(REPEAT_HANDSHAKE).send();
            Logs.CONN.warn("handshake failure, repeat handshake, conn={}", message.getConnection());
            return;
        }

        //6.响应握手成功消息
        HandshakeOkMessage.from(message).send();

        //8.保存client信息到当前连接
        context.setOsName(message.osName)
                .setOsVersion(message.osVersion)
                .setClientVersion(message.clientVersion)
                .setDeviceId(message.deviceId)
                .setHeartbeat(Integer.MAX_VALUE);

        Logs.CONN.info("handshake success, conn={}", message.getConnection());

    }
}