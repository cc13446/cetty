package com.cc.cetty.attribute;

/**
 * @author: cc
 * @date: 2023/11/3
 */
public interface AttributeMap {

    <T> Attribute<T> attr(AttributeKey<T> key);

    <T> boolean hasAttr(AttributeKey<T> key);
}

