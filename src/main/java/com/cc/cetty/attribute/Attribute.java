package com.cc.cetty.attribute;

/**
 * 该接口是AttributeMap中存储的value的实现类的接口
 * @author: cc
 * @date: 2023/11/3
 */
public interface Attribute<T> {

    AttributeKey<T> key();

    T get();

    void set(T value);

    T getAndSet(T value);

    T setIfAbsent(T value);

    T getAndRemove();

    boolean compareAndSet(T oldValue, T newValue);

    void remove();
}
