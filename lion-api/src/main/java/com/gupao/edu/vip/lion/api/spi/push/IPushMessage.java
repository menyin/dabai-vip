

package com.gupao.edu.vip.lion.api.spi.push;

import com.gupao.edu.vip.lion.api.common.Condition;

/**
 *
 *
 */
public interface IPushMessage {

    boolean isBroadcast();//cny_note 是否是广播类型消息

    String getUserId();//cny_note 用户业务Id

    int getClientType();//cny_note 设备类型分类，应该是通过一个分类器扩展点得到的int值

    byte[] getContent();//cny_note 实际推送的内容

    boolean isNeedAck();//cny_note 是否需要应答给商家

    byte getFlags();

    int getTimeoutMills();//cny_note 超时的毫秒数，超时则将应答任务取消

    default String getTaskId() {
        return null;
    }

    default Condition getCondition() {
        return null;
    }

    default void finalized() {

    }

}
