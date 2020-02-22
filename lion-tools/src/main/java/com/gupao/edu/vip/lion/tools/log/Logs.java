
package com.gupao.edu.vip.lion.tools.log;

import com.gupao.edu.vip.lion.tools.config.CC;
import com.typesafe.config.ConfigRenderOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 */
public interface Logs {
    boolean logInit = init();

    static boolean init() {
        //cny_note 因为静态代码块在类实例加载前，而日志的使用一般都在类实例加载后，所以在此设置日志框架所需环境变量
        if (logInit) return true;
        System.setProperty("log.home", CC.lion.log_dir);
        System.setProperty("log.root.level", CC.lion.log_level);//设置系统环境变量  日志级别
        System.setProperty("logback.configurationFile", CC.lion.log_conf_path);
        LoggerFactory
                .getLogger("console")
                .info(CC.lion.cfg.root().render(ConfigRenderOptions.concise().setFormatted(true)));//cny_note 打印lion.conf里的所有配置文本
        return true;
    }

    Logger Console = LoggerFactory.getLogger("console"),

    CONN = LoggerFactory.getLogger("lion.conn.log"),

    MONITOR = LoggerFactory.getLogger("lion.monitor.log"),

    PUSH = LoggerFactory.getLogger("lion.push.log"),

    HB = LoggerFactory.getLogger("lion.heartbeat.log"),

    CACHE = LoggerFactory.getLogger("lion.cache.log"),

    RSD = LoggerFactory.getLogger("lion.srd.log"),//？？

    HTTP = LoggerFactory.getLogger("lion.http.log"),

    PROFILE = LoggerFactory.getLogger("lion.profile.log");//？？
}
