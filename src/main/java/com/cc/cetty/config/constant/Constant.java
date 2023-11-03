package com.cc.cetty.config.constant;

/**
 * 常量类的顶级接口，定义了常量的id和名字
 *
 * @author: cc
 * @date: 2023/11/3
 */
public interface Constant<T extends Constant<T>> extends Comparable<T> {

    int id();

    String name();

}