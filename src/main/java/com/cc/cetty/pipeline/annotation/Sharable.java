package com.cc.cetty.pipeline.annotation;

import java.lang.annotation.*;

/** 是否可以共用
 * @author: cc
 * @date: 2023/11/05
 **/
@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Sharable {
}
