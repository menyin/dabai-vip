
package com.gupao.edu.vip.lion.network.netty.codec;

import com.gupao.edu.vip.lion.api.protocol.JsonPacket;
import com.gupao.edu.vip.lion.api.protocol.Packet;
import com.gupao.edu.vip.lion.api.protocol.UDPPacket;
import com.gupao.edu.vip.lion.tools.Jsons;
import com.gupao.edu.vip.lion.tools.config.CC;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;

import java.util.List;

import static com.gupao.edu.vip.lion.api.protocol.Packet.decodePacket;

/**
 * length(4)+cmd(1)+cc(2)+flags(1)+sessionId(4)+lrc(1)+body(n)
 *
 */
public final class PacketDecoder extends ByteToMessageDecoder {
    private static final int maxPacketSize = CC.lion.core.max_packet_size;

    /**
     *
     * @param ctx
     * @param in 接收到的数据的缓冲ByteBuf
     * @param out 解码转换为对象放置在这个list里，并且会传递到管道中的下一个handler
     * @throws Exception
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        decodeHeartbeat(in, out);
        decodeFrames(in, out);
    }

    /**
     * cny_note 读取判断看是不是心跳包
     * @param in
     * @param out
     */
    private void decodeHeartbeat(ByteBuf in, List<Object> out) {
        while (in.isReadable()) {
            if (in.readByte() == Packet.HB_PACKET_BYTE) {//cny_note 是心跳字节说明是心跳包  //此处可以用in.getByte(in.readerIndex()) 替代，就不用进行索引重置
                out.add(Packet.HB_PACKET); //cny_note Packet.HB_PACKET至少一个cmd为Command.HEARTBEAT的Packet对象
            } else {
                in.readerIndex(in.readerIndex() - 1); //cny_note 应该是使得读指针再回到首位
                break;
            }
        }
    }

    /**
     * cny_note  这里的in 应该是每个通道对应一个ByteBuf和List<Object> 前者应该是缓存字节，而后者应是缓存解码后的对象
     *
     * @param in
     * @param out
     */
    private void decodeFrames(ByteBuf in, List<Object> out) {
        if (in.readableBytes() >= Packet.HEADER_LEN) { //当读取的字节至少一个头的字节数才才盛处理  ？？如果刻度字节数小于HEADER_LEN怎么办，
            //1.记录当前读取位置位置.如果读取到非完整的frame,要恢复到该位置,便于下次读取
            in.markReaderIndex();

            Packet packet = decodeFrame(in);
            if (packet != null) {
                out.add(packet);
            } else {
                //2.读取到不完整的frame,恢复到最近一次正常读取的位置,便于下次读取
                in.resetReaderIndex(); //cny_note 这里应该是重回到in.markReaderIndex();标记的地方
            }
        }
    }

    private Packet decodeFrame(ByteBuf in) {
        int readableBytes = in.readableBytes();
        int bodyLength = in.readInt();//cny_note 此句执行完 读指针已经移动到第二个字节了，即cmd字节
        if (readableBytes < (bodyLength + Packet.HEADER_LEN)) {//cny_note 如果读取的字节数小于 一个完整数据包的长度 return null
            return null;//读索引重置会放在decodeFrame()的调用处
        }
        if (bodyLength > maxPacketSize) {//cny_note  对数据包有限制
            throw new TooLongFrameException("packet body length over limit:" + bodyLength);
        }
        return decodePacket(new Packet(in.readByte()), in, bodyLength); //cny_note 这里in.readByte()读取的是cmd字节
    }

    public static Packet decodeFrame(DatagramPacket frame) {
        ByteBuf in = frame.content();
        int readableBytes = in.readableBytes();
        int bodyLength = in.readInt();
        if (readableBytes < (bodyLength + Packet.HEADER_LEN)) {
            return null;
        }

        return decodePacket(new UDPPacket(in.readByte()
                , frame.sender()), in, bodyLength);
    }

    public static Packet decodeFrame(String frame) throws Exception {
        if (frame == null) return null;
        return Jsons.fromJson(frame, JsonPacket.class);
    }
}