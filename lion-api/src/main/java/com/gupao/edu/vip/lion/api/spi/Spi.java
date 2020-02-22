
package com.gupao.edu.vip.lion.api.spi;

import java.lang.annotation.*;

/**
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Spi {

    /**
     * SPI name
     *
     * @return name
     */
    String value() default "";

    /**
     * 排序顺序
     * cny 按此序号进行升序排序，并且SPI只取第一个
     * @return sortNo
     */
    int order() default 0;

}
