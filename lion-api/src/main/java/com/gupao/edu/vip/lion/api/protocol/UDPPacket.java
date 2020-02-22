
package com.gupao.edu.vip.lion.api.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramPacket;

import java.net.InetSocketAddress;


public final class UDPPacket extends Packet {
    private InetSocketAddress address;

    public UDPPacket(byte cmd, InetSocketAddress sender) {
        super(cmd);
        this.address = sender;
    }

    public UDPPacket(Command cmd, int sessionId, InetSocketAddress sender) {
        super(cmd, sessionId);
        this.address = sender;
    }

    public UDPPacket(byte cmd) {
        super(cmd);
    }

    public UDPPacket(Command cmd) {
        super(cmd);
    }

    public UDPPacket(Command cmd, int sessionId) {
        super(cmd, sessionId);
    }

    @Override
    public InetSocketAddress sender() {
        return address;
    }

    @Override
    public void setRecipient(InetSocketAddress recipient) {
        this.address = recipient;
    }

    @Override
    public Packet response(Command command) {
        return new UDPPacket(command, sessionId, address);
    }

    @Override
    public Object toFrame(Channel channel) {
        int capacity = cmd == Command.HEARTBEAT.cmd ? 1 : HEADER_LEN + getBodyLength();
        ByteBuf out = channel.alloc().buffer(capacity, capacity);//一参表起始容量，二参表最大容量//channel.alloc()得到的是一个ByteBuf池实例
        encodePacket(this, out);
        return new DatagramPacket(out, sender());
    }
}
