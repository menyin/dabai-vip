
package com.gupao.edu.vip.lion.core.session;

import com.gupao.edu.vip.lion.api.connection.SessionContext;
import com.gupao.edu.vip.lion.api.spi.common.CacheManager;
import com.gupao.edu.vip.lion.api.spi.common.CacheManagerFactory;
import com.gupao.edu.vip.lion.common.CacheKeys;
import com.gupao.edu.vip.lion.tools.common.Strings;
import com.gupao.edu.vip.lion.tools.config.CC;
import com.gupao.edu.vip.lion.tools.crypto.MD5Utils;

/**
 * 管理可重复使用的Session
 */
public final class ReusableSessionManager {
    private final int expiredTime = CC.lion.core.session_expired_time;
    private final CacheManager cacheManager = CacheManagerFactory.create();

    /**
     * cny_note 缓存session，其实是将客户端的相关信息序列化缓存起来
     * @param session
     * @return
     */
    public boolean cacheSession(ReusableSession session) {
        String key = CacheKeys.getSessionKey(session.sessionId);
        cacheManager.set(key, ReusableSession.encode(session.context), expiredTime);
        return true;
    }

    /**
     * 查询ReusableSession
     * @param sessionId
     * @return
     */
    public ReusableSession querySession(String sessionId) {
        String key = CacheKeys.getSessionKey(sessionId);
        String value = cacheManager.get(key, String.class);
        if (Strings.isBlank(value)) return null;
        return ReusableSession.decode(value);
    }

    /**
     * cny_note 生成ReuseableSession
     * @param context
     * @return
     */
    public ReusableSession genSession(SessionContext context) {
        long now = System.currentTimeMillis();
        ReusableSession session = new ReusableSession();
        session.context = context;
        session.sessionId = MD5Utils.encrypt(context.deviceId + now);
        session.expireTime = now + expiredTime * 1000;
        return session;
    }
}
