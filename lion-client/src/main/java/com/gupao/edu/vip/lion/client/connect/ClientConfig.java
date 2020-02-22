
package com.gupao.edu.vip.lion.client.connect;

/**
 * cny_note 骑手客户端与服务端建立连接所需要的参数配置类
 */
public class ClientConfig {
    private byte[] clientKey;//cny_note 客户端随机数16位
    private byte[] iv;//cny_note AES密钥向量16位 cny_note 相当于加盐，并且这个盐前后端都是一致的
    private String clientVersion;//cny_note 客户端版本
    private String deviceId;
    private String osName;
    private String osVersion;
    private String userId;
    private String cipher; //快速重连的时候使用  cny_note 当与服务端建立连接成功后由服务端返回给客户端


    public byte[] getClientKey() {
        return clientKey;
    }

    public void setClientKey(byte[] clientKey) {
        this.clientKey = clientKey;
    }

    public byte[] getIv() {
        return iv;
    }

    public void setIv(byte[] iv) {
        this.iv = iv;
    }

    public String getClientVersion() {
        return clientVersion;
    }

    public void setClientVersion(String clientVersion) {
        this.clientVersion = clientVersion;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public String getCipher() {
        return cipher;
    }

    public void setCipher(String cipher) {
        this.cipher = cipher;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "{" +
                "deviceId='" + deviceId + '\'' +
                ", userId='" + userId + '\'' +
                '}';
    }
}
