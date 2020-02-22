
package com.gupao.edu.vip.lion.common.message;

import com.gupao.edu.vip.lion.api.connection.Cipher;
import com.gupao.edu.vip.lion.api.connection.Connection;
import com.gupao.edu.vip.lion.api.message.Message;
import com.gupao.edu.vip.lion.api.protocol.Packet;
import com.gupao.edu.vip.lion.tools.Jsons;
import com.gupao.edu.vip.lion.tools.common.IOUtils;
import com.gupao.edu.vip.lion.tools.common.Profiler;
import com.gupao.edu.vip.lion.tools.config.CC;
import io.netty.channel.ChannelFutureListener;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * cny_note 主要将packet和connection关联起来
 *          负责xxxMessage实例编解码中的加解密操作、编解码方式的选择、当前消息发送等操作
 */
public abstract class BaseMessage implements Message {
    private static final byte STATUS_DECODED = 1;
    private static final byte STATUS_ENCODED = 2;
    private static final AtomicInteger ID_SEQ = new AtomicInteger();//cny_note 全局递增
    transient protected Packet packet;
    transient protected Connection connection;
    transient private byte status = 0;//？？有什么用  ！！应该只是标志已经解码或已经编码的状态标志

    /**
     * cny_note 基类没有无参构造函数，则子类的构造函数必须调用一些父类的有参构造函数
     * @param packet
     * @param connection
     */
    public BaseMessage(Packet packet, Connection connection) {
        this.packet = packet;
        this.connection = connection;
    }

    @Override
    public void decodeBody() {
        if ((status & STATUS_DECODED) == 0) {
            status |= STATUS_DECODED;

            if (packet.getBodyLength() > 0) {
                if (packet.hasFlag(Packet.FLAG_JSON_BODY)) {
                    decodeJsonBody0(); //cny_note 会解析成具体xxxMessage的属性
                } else {
                    decodeBinaryBody0();//cny_note 会进行解密解压缩成byte[]类型实例在传递给xxxMessage进行解析，有可能解析成属性或其它
                }
            }

        }
    }

    @Override
    public void encodeBody() {
        if ((status & STATUS_ENCODED) == 0) {
            status |= STATUS_ENCODED;

            if (packet.hasFlag(Packet.FLAG_JSON_BODY)) {
                encodeJsonBody0();
            } else {
                encodeBinaryBody0();
            }
        }
    }


    private void decodeBinaryBody0() {
        //1.解密
        byte[] tmp = packet.body;
        if (packet.hasFlag(Packet.FLAG_CRYPTO)) {
            if (getCipher() != null) {
                tmp = getCipher().decrypt(tmp);
            }
        }
        //2.解压
        if (packet.hasFlag(Packet.FLAG_COMPRESS)) {
            tmp = IOUtils.decompress(tmp);
        }

        if (tmp.length == 0) {
            throw new RuntimeException("message decode ex");
        }

        packet.body = tmp;
        Profiler.enter("time cost on [body decode]");
        decode(packet.body);
        Profiler.release();
        packet.body = null;// 释放内存 //？？为什么这么搞
    }

    private void encodeBinaryBody0() {
        Profiler.enter("time cost on [body encode]");
        byte[] tmp = encode();
        Profiler.release();
        if (tmp != null && tmp.length > 0) {
            //1.压缩
            if (tmp.length > CC.lion.core.compress_threshold) {//cny_note  超过指定字节数才进行压缩
                byte[] result = IOUtils.compress(tmp);
                if (result.length > 0) {
                    tmp = result;
                    packet.addFlag(Packet.FLAG_COMPRESS);
                }
            }

            //2.加密
            Cipher cipher = getCipher();
            if (cipher != null) {
                byte[] result = cipher.encrypt(tmp);
                if (result.length > 0) {
                    tmp = result;
                    packet.addFlag(Packet.FLAG_CRYPTO);
                }
            }
            packet.body = tmp;
        }
    }

    private void decodeJsonBody0() {
        Map<String, Object> body = packet.getBody();
        decodeJsonBody(body);
    }

    private void encodeJsonBody0() {
        packet.setBody(encodeJsonBody());
    }

    private void encodeJsonStringBody0() {
        packet.setBody(encodeJsonStringBody());
    }

    protected String encodeJsonStringBody() {
        return Jsons.toJson(this);
    }

    /**
     * cny_note 编码成未压缩加密的数据
     */
    private void encodeBodyRaw() {
        if ((status & STATUS_ENCODED) == 0) {
            status |= STATUS_ENCODED;

            if (packet.hasFlag(Packet.FLAG_JSON_BODY)) {
                encodeJsonBody0();
            } else {
                packet.body = encode();// 这里没有再经过压缩加密步骤
            }
        }
    }

    //cny_note body已经是解压解码后的字节数组，交由子类去处理
    public abstract void decode(byte[] body);

    public abstract byte[] encode();

    /**
     * cny_note 如果是安全消息类型子类，即将packet.body解析为子类实例的属性
     *          此时安全消息类型子类就需要重写此方法
     * @param body
     */
    protected void decodeJsonBody(Map<String, Object> body) {

    }

    /**
     * cny_note 子类去重写，让子类将一些数据塞到Map类型下的body字段
     * @return
     */
    protected Map<String, Object> encodeJsonBody() {
        return null;
    }

    @Override
    public Packet getPacket() {
        return packet;
    }

    @Override
    public Connection getConnection() {
        return connection;
    }

    @Override
    public void send(ChannelFutureListener listener) {
        encodeBody();
        connection.send(packet, listener);
    }

    @Override
    public void sendRaw(ChannelFutureListener listener) {
        encodeBodyRaw();
        connection.send(packet, listener);
    }

    /**
     * cny_note 简单发送消息，不对发送成功与否结果做处理。sendRaw同理
     */
    public void send() {
        send(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);//cny_note 发送完成后的结果是成功则没有任何处理，否则交由netty的业务handle里exceptionCaught处理
    }

    public void sendRaw() {
        sendRaw(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
    }

    public void close() {
        send(ChannelFutureListener.CLOSE);//cny_note 在发送完成后，无论成功失败都执行关闭操作，具体可以进入CLOSE源码查看
    }

    //？？什么情况下会设置这个sessionId ！所有由客户端（包括商家和骑手）发起的消息都要有这个设置，它是一个消息的唯一标识。
    protected static int genSessionId() {
        return ID_SEQ.incrementAndGet();
    }

    public int getSessionId() {
        return packet.sessionId;
    }

    public BaseMessage setRecipient(InetSocketAddress recipient) {
        packet.setRecipient(recipient);
        return this;
    }

    public void setPacket(Packet packet) {
        this.packet = packet;
    }

    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    public ScheduledExecutorService getExecutor() {
        return connection.getChannel().eventLoop();
    }

    public void runInRequestThread(Runnable runnable) {
        connection.getChannel().eventLoop().execute(runnable);
    }

    protected Cipher getCipher() {
        return connection.getSessionContext().cipher;
    }

    @Override
    public abstract String toString();
}
