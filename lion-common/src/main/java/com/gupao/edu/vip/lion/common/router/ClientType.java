
package com.gupao.edu.vip.lion.common.router;

import java.util.Arrays;

/**
 *
 *
 */
public enum ClientType {
    MOBILE(1, "android", "ios"),
    PC(2, "windows", "mac", "linux"),
    WEB(3, "web", "h5"),
    UNKNOWN(-1);

    public final int type;
    public final String[] os;

    ClientType(int type, String... os) {
        this.type = type;
        this.os = os;
    }

    /**
     * 判断osName是否属于当前实例
     * @param osName
     * @return
     */
    public boolean contains(String osName) {
        return Arrays.stream(os).anyMatch(osName::contains);
    }

    /**
     * 判断osName是属于什么ClientType实例
     * @param osName
     * @return
     */
    public static ClientType find(String osName) {
        for (ClientType type : values()) {
            if (type.contains(osName.toLowerCase())) return type;
        }
        return UNKNOWN;
    }

    public static boolean isSameClient(String osNameA, String osNameB) {
        if (osNameA.equals(osNameB)) return true;
        return find(osNameA).contains(osNameB);
    }
}
