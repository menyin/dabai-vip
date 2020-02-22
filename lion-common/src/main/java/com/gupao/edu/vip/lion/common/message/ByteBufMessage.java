
package com.gupao.edu.vip.lion.common.message;

import com.gupao.edu.vip.lion.api.Constants;
import com.gupao.edu.vip.lion.api.connection.Connection;
import com.gupao.edu.vip.lion.api.protocol.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * cny_note 为BaseMessage里packet.body字节数组进行编解码时,用ByteBuf对字节数组进行包装，并提供操作ByteBuf的一些公共操作抽象
 */
public abstract class ByteBufMessage extends BaseMessage {

    public ByteBufMessage(Packet message, Connection connection) {
        super(message, connection);
    }

    @Override
    public void decode(byte[] body) {
        decode(Unpooled.wrappedBuffer(body));//cny_note Unpooled是netty提供的工具，用于将byte[]转为ByteBuf
    }

    /**
     * 其实ByteBufMessage类只是在该方法使用ByteBuf进行body的字节数组进行包装，以方便子类编解码字节数组
     * @return
     */
    @Override
    public byte[] encode() {
        //创建一个堆内输出流ByteBuf对象
        ByteBuf body = connection.getChannel().alloc().heapBuffer(); //.alloc()得到ByteBufAllocator实例，详见咕泡《07-Buffer API.pdf》，这里应该是一个全新的ByteBuf
        try {
            encode(body);
            byte[] bytes = new byte[body.readableBytes()];
            body.readBytes(bytes);
            return bytes;
        } finally {
            body.release();
        }
    }

    public abstract void decode(ByteBuf body);

    public abstract void encode(ByteBuf body);

    /**
     * cny_note 编码String类型属性
     * @param body 输出的ByteBuf
     * @param field 属性值
     */
    public void encodeString(ByteBuf body, String field) {
        encodeBytes(body, field == null ? null : field.getBytes(Constants.UTF_8));
    }

    /**
     * cny_note 编码byte类型属性
     * @param body 输出的ByteBuf
     * @param field 属性值
     */
    public void encodeByte(ByteBuf body, byte field) {
        body.writeByte(field);
    }

    /**
     * cny_note 编码int类型属性
     * @param body 输出的ByteBuf
     * @param field 属性值
     */
    public void encodeInt(ByteBuf body, int field) {
        body.writeInt(field);
    }

    /**
     * cny_note 编码long类型属性
     * @param body 输出的ByteBuf
     * @param field 属性值
     */
    public void encodeLong(ByteBuf body, long field) {
        body.writeLong(field);
    }

    /**
     * cny_note 编码规则和解码规则相对应看
     *          注意编码规则是：前一个或两个位置代表属性字节数组的长度
     * @param body
     * @param field
     */
    public void encodeBytes(ByteBuf body, byte[] field) {
        if (field == null || field.length == 0) {
            body.writeShort(0);
        } else if (field.length < Short.MAX_VALUE) {
            body.writeShort(field.length).writeBytes(field);
        } else {
            body.writeShort(Short.MAX_VALUE).writeInt(field.length - Short.MAX_VALUE).writeBytes(field);
        }
    }



    public String decodeString(ByteBuf body) {
        byte[] bytes = decodeBytes(body);
        if (bytes == null) return null;
        return new String(bytes, Constants.UTF_8);
    }

    public byte[] decodeBytes(ByteBuf body) {
        int fieldLength = body.readShort();//cny_note 读指针已移动
        if (fieldLength == 0) return null;
        if (fieldLength == Short.MAX_VALUE) {
            fieldLength += body.readInt();//cny_note 读指针已移动
        }
        byte[] bytes = new byte[fieldLength];
        body.readBytes(bytes);
        return bytes;
    }

    public byte decodeByte(ByteBuf body) {
        return body.readByte();
    }

    public int decodeInt(ByteBuf body) {
        return body.readInt();
    }

    public long decodeLong(ByteBuf body) {
        return body.readLong();
    }
}
