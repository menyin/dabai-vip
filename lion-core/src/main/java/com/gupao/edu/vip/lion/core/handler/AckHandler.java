
package com.gupao.edu.vip.lion.core.handler;

import com.gupao.edu.vip.lion.api.connection.Connection;
import com.gupao.edu.vip.lion.api.protocol.Packet;
import com.gupao.edu.vip.lion.common.handler.BaseMessageHandler;
import com.gupao.edu.vip.lion.common.message.AckMessage;
import com.gupao.edu.vip.lion.core.LionServer;
import com.gupao.edu.vip.lion.core.ack.AckTask;
import com.gupao.edu.vip.lion.core.ack.AckTaskQueue;
import com.gupao.edu.vip.lion.tools.log.Logs;

/**
 * cny_note 这个handler是当商家推送给骑手消息后，骑手应答的消息，应答消息是发送到connectionServer
 */
public final class AckHandler extends BaseMessageHandler<AckMessage> {

    private final AckTaskQueue ackTaskQueue;

    public AckHandler(LionServer lionServer) {
        this.ackTaskQueue = lionServer.getPushCenter().getAckTaskQueue();
    }


    @Override
    public AckMessage decode(Packet packet, Connection connection) {
        return new AckMessage(packet, connection);
    }

    @Override
    public void handle(AckMessage message) {
        AckTask task = ackTaskQueue.getAndRemove(message.getSessionId());//cny_note 取出对应的应答任务
        if (task == null) {//ack 超时了 cny_note 这个任务已经在超时的时候被移除掉了
            Logs.PUSH.info("receive client ack, but task timeout message={}", message);
            return;
        }

        task.onResponse();//成功收到客户的ACK响应
    }
}
