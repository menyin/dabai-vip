

package com.gupao.edu.vip.lion.api.connection;


import com.gupao.edu.vip.lion.api.router.ClientClassifier;

/**
 * 与当前连接相关的客户端信息的聚合类
 */
public final class SessionContext {
    public String osName;
    public String osVersion;
    public String clientVersion;
    public String deviceId;
    public String userId;
    public String tags;
    public int heartbeat = 10000;// 10s
    public Cipher cipher;//加密工具，可以是RSA或AES  ？这里居然是用public 可能是失误
    private byte clientType;//cny_note 通过分类器对osName进行分类可以得到，通过该字段可以对设备类型进行分类

    public void changeCipher(Cipher cipher) {
        this.cipher = cipher;
    }

    public SessionContext setOsName(String osName) {
        this.osName = osName;
        return this;
    }

    public SessionContext setOsVersion(String osVersion) {
        this.osVersion = osVersion;
        return this;
    }

    public SessionContext setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
        return this;
    }

    public SessionContext setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    public SessionContext setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    public void setHeartbeat(int heartbeat) {
        this.heartbeat = heartbeat;
    }

    public boolean handshakeOk() {
        return deviceId != null && deviceId.length() > 0;
    }

    public int getClientType() {
        if (clientType == 0) {//cny_note byte成员变量初始化值为0
            clientType = (byte) ClientClassifier.I.getClientType(osName);
        }
        return clientType;
    }

    public boolean isSecurity() {
        return cipher != null;
    }

    @Override
    public String toString() {
        if (userId == null && deviceId == null) {
            return "";
        }

        return "{" +
                "osName='" + osName + '\'' +
                ", osVersion='" + osVersion + '\'' +
                ", deviceId='" + deviceId + '\'' +
                ", userId='" + userId + '\'' +
                ", tags='" + tags + '\'' +
                ", heartbeat=" + heartbeat +
                '}';
    }
}
