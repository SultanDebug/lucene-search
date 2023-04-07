package com.hzq.plugins.annotations;

import java.lang.annotation.*;

/**
 * @author Huangzq
 * @description
 * @date 2023/4/4 16:43
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyPlugin {
    String desc();
}
