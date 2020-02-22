
package com.gupao.edu.vip.lion.common;

/**
 * cny_note 为了缓存中的key有一些固定的规则，统一由此类类生成key字符串
 *          从此类可以窥探到都有哪些东西放到缓存
 */
public final class CacheKeys {

    private static final String USER_PREFIX = "lion:ur:";//用户路由

    private static final String SESSION_PREFIX = "lion:rs:";//可复用session

    private static final String FAST_CONNECTION_DEVICE_PREFIX = "lion:fcd:";

    private static final String ONLINE_USER_LIST_KEY_PREFIX = "lion:oul:";//在线用户列表

    public static final String SESSION_AES_KEY = "lion:sa";
    public static final String SESSION_AES_SEQ_KEY = "lion:sas";
    public static final String PUSH_TASK_PREFIX = "lion:pt";

    public static String getUserRouteKey(String userId) {
        return USER_PREFIX + userId;
    }

    public static String getSessionKey(String sessionId) {
        return SESSION_PREFIX + sessionId;
    }

    public static String getDeviceIdKey(String deviceId) {
        return FAST_CONNECTION_DEVICE_PREFIX + deviceId;
    }

    /**
     * cny_note 在redis上存储在线用户列表的key
     * @param publicIP  ？？外网地址 应该是redis外网的地址吧
     * @return
     */
    public static String getOnlineUserListKey(String publicIP) {
        return ONLINE_USER_LIST_KEY_PREFIX + publicIP;
    }

    public static String getPushTaskKey(String taskId) {
        return PUSH_TASK_PREFIX + taskId;
    }

}
