
package com.gupao.edu.vip.lion.api.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;

/**
 * length(4)+cmd(1)+cc(2)+flags(1)+sessionId(4)+lrc(1)+body(n)
 * cny_note 所有通信包的基类
 */
@SuppressWarnings("unchecked")//cny_note 取消编译器的警告
public class Packet {
    public static final int HEADER_LEN = 13; //协议header的长度 ,这个长度是固定的，但是body部分的长度是

    //cny_note 这些枚举值对应 flags的5个位进行存储，从websocket请求包来看
    public static final byte FLAG_CRYPTO = 1; //加密
    public static final byte FLAG_COMPRESS = 2; //压缩
    public static final byte FLAG_BIZ_ACK = 4; //cny_note 由客户端业务自己确认消息是否到达
    public static final byte FLAG_AUTO_ACK = 8;
    public static final byte FLAG_JSON_BODY = 16;//cny_note xxxPacket子类中只有JsonPacket实例化时有此标志，在解码过程中会转换为对应xxxMessage对象的属性。
                                                 //cny_note JsonPacket是不会经过加密器cipher，非JsonPacket则会cipher加密

    public static final byte HB_PACKET_BYTE = -33; //？？这个是什么 ！如果是一个心跳包，那边它的首字节就是-33，并且整个包就就一个字节
    public static final byte[] HB_PACKET_BYTES = new byte[]{HB_PACKET_BYTE};//？？这个是什么 ！包含唯一一个字节的字节数组，就是一个心跳包对应的字节数组
    public static final Packet HB_PACKET = new Packet(Command.HEARTBEAT);//cny_note 心跳包Packet对象
    /**
     * cny_note 为什么这里的字段都没有遵循javabean的get-set规范
     */
    public byte cmd; //命令 cny_note Command枚举类实例的cmd字段 代表这个包执行命令的类型
    transient public short cc; //校验码 暂时没有用到
    public byte flags; //特性，如是否加密，是否压缩等
    public int sessionId; // 会话id。客户端生成。 ？？此sessionId和xxxMessage里的sessionId有什么区别。  后者是用于快速认证的
    transient public byte lrc; // 校验，纵向冗余校验。只校验head ？？有空可以学习下
    transient public byte[] body; //？？为什么body不被序列化，加transient意义何在。 而且Packet对象并没有被序列化，而是通过Encoder转换为ByteBuf里的字节

    public Packet(byte cmd) {
        this.cmd = cmd;
    }

    public Packet(byte cmd, int sessionId) {
        this.cmd = cmd;
        this.sessionId = sessionId;
    }

    public Packet(Command cmd) {
        this.cmd = cmd.cmd;
    }

    public Packet(Command cmd, int sessionId) {
        this.cmd = cmd.cmd;
        this.sessionId = sessionId;
    }

    public int getBodyLength() {
        return body == null ? 0 : body.length;
    }

    /**
     * cny_note 只能用于0置为1操作，如果要1置为0 则需要用removeFlag
     * @param flag
     */
    public void addFlag(byte flag) {//？这个只能是添加flag，不能修改为"否",如flags已经是加密位为1，这时加密位要置为0，怎么运算:flag取反 再喝flags相与（按位与），实现code:removeFlag
        this.flags |= flag;
    }

    /**
     * cny_add
     * code:removeFlag
     * @param flag
     */
    /*public void removeFlag(byte flag) {
        this.flags &= ~flag;//？
    }*/

    public boolean hasFlag(byte flag) {
        return (flags & flag) != 0;
    }

    /**
     * cny_note 这个模式可以学习下：当子类重写父类的泛型方法时可以指定具体的T类型
     *          注意如果这个Packet被实例化，则调用getBody方法时，接收的变量类型和body不能转换则会出错的
     * @param <T>
     * @return
     */
    public <T> T getBody() { //cny_note 和setBody一样，应该是会被子类重写
        return (T) body;
    }

    public <T> void setBody(T body) {
        this.body = (byte[]) body; //？？这个似乎不能这样转换，会报错。！应该会被子类重写，而实际上通常是要使用子类的setBody
    }

    public short calcCheckCode() {
        short checkCode = 0;
        if (body != null) {
            for (int i = 0; i < body.length; i++) {
                checkCode += (body[i] & 0x0ff); //cny_note
            }
        }
        return checkCode;
    }

    /**
     * cny_note 纵向冗余校验和普通校验都是为了校验body数据的真实性。 普通校验可能计算过程中唯一性不能保证，所以进一步用冗余校验验证
     * @return
     */
    public byte calcLrc() {
        byte[] data = Unpooled.buffer(HEADER_LEN - 1)
                .writeInt(getBodyLength())
                .writeByte(cmd)
                .writeShort(cc)
                .writeByte(flags)
                .writeInt(sessionId)
                .array();
        byte lrc = 0;
        for (int i = 0; i < data.length; i++) {
            lrc ^= data[i];
        }
        return lrc;
    }

    public boolean validCheckCode() {
        return calcCheckCode() == cc;
    }

    public boolean validLrc() {
        return (lrc ^ calcLrc()) == 0;
    }
    /**
     * cny_note 获取数据包的接受者
     * cny_note ？？应该是数据包的接收者 主要让子类重写，默认为null 这个应该叫做getRecipient会更合适点
     * @param
     */
    public InetSocketAddress sender() {
        return null;
    }

    /**
     * cny_note 设置数据包的接受者
     * @param sender
     */
    public void setRecipient(InetSocketAddress sender) {
    }

    /**
     * cny_note 返回携带cmd和sessionId的响应Packet
     * @param command
     * @return
     */
    public Packet response(Command command) {
        return new Packet(command, sessionId);
    }

    public Object toFrame(Channel channel) {//？？为什么要传递参数 ！给子类继承，参数只为兼容适配
        return this;
    }

    /**
     * ？编解码的过程为什么不是对应的，packet.cmd为什么没有解码 ！在调用此方法时传递的packet对象已经是有解码的cmd
     * @param packet
     * @param in 注意此时in的指针已指向第3个字节了，即cc/checkcode处
     * @param bodyLength
     * @return
     */
    public static Packet decodePacket(Packet packet, ByteBuf in, int bodyLength) {
        packet.cc = in.readShort();//read cc  //cny_note 这里Bytebuf的读索引会直接更新到第一个Short类型数据的位置，下面代码再进行读操作读索引就一直递增
        packet.flags = in.readByte();//read flags
        packet.sessionId = in.readInt();//read sessionId
        packet.lrc = in.readByte();//read lrc

        //read body
        if (bodyLength > 0) {
            in.readBytes(packet.body = new byte[bodyLength]);//也可以用packet.setBody(byteBuf.readBytes(bodyLength).array());
        }
        return packet;
    }

    /**
     * cny_note 将packet转为bytebuf
     * @param packet
     * @param out
     */
    public static void encodePacket(Packet packet, ByteBuf out) {//？？ByteBuf操作有待学习
        if (packet.cmd == Command.HEARTBEAT.cmd) {//心跳包
            out.writeByte(Packet.HB_PACKET_BYTE);
        } else {
            out.writeInt(packet.getBodyLength());
            out.writeByte(packet.cmd);
            out.writeShort(packet.cc);
            out.writeByte(packet.flags);
            out.writeInt(packet.sessionId);
            out.writeByte(packet.lrc);
            if (packet.getBodyLength() > 0) {
                out.writeBytes(packet.body);
            }
        }
        packet.body = null;//？？为什么置空，大白说因为只是对头进行处理。感觉很牵强的理由
        // ！应该是一个packet包=header+body 而对于一个连接用户来说header是不变，body是可变。 因此每次只改变body，每次都清零可以让GC抓紧回收。待验证
    }

    @Override
    public String toString() {
        return "{" +
                "cmd=" + cmd +
                ", cc=" + cc +
                ", flags=" + flags +
                ", sessionId=" + sessionId +
                ", lrc=" + lrc +
                ", body=" + (body == null ? 0 : body.length) +
                '}';
    }

    public static ByteBuf getHBPacket() {
        return Unpooled.wrappedBuffer(HB_PACKET_BYTES); //？？这个是什么 ！应该是构建一个只有心跳字节/心跳包的ByteBuf对象
    }
}
