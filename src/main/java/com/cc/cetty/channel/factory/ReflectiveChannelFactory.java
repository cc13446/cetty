package com.cc.cetty.channel.factory;

import com.cc.cetty.channel.Channel;
import com.cc.cetty.utils.AssertUtils;

import java.lang.reflect.Constructor;

/**
 * @author: cc
 * @date: 2023/11/3
 */
public class ReflectiveChannelFactory<T extends Channel> implements ChannelFactory<T> {

    private final Constructor<? extends T> constructor;

    public ReflectiveChannelFactory(Class<? extends T> clazz) {
        AssertUtils.checkNotNull(clazz);
        try {
            this.constructor = clazz.getConstructor();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + clazz.getSimpleName() + " does not have a public non-arg constructor", e);
        }
    }

    @Override
    public T newChannel() {
        try {
            return constructor.newInstance();
        } catch (Throwable t) {
            throw new RuntimeException("Unable to create Channel from class " + constructor.getDeclaringClass(), t);
        }
    }

    @Override
    public String toString() {
        return ReflectiveChannelFactory.class.getSimpleName() + '(' + constructor.getDeclaringClass().getSimpleName() + ".class)";
    }
}
