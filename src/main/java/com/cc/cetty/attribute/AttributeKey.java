package com.cc.cetty.attribute;

import com.cc.cetty.config.constant.AbstractConstant;
import com.cc.cetty.config.constant.ConstantPool;

/**
 * 是一个常量类，把map的key包装为常量
 * @author: cc
 * @date: 2023/11/3
 */
public final class AttributeKey<T> extends AbstractConstant<AttributeKey<T>> {

    /**
     * 常量池
     */
    private static final ConstantPool<AttributeKey<Object>> POOL = new ConstantPool<>() {
        @Override
        protected AttributeKey<Object> newConstant(int id, String name) {
            return new AttributeKey<>(id, name);
        }
    };

    /**
     * @param name name
     * @return value
     * @param <T> T
     */
    @SuppressWarnings("unchecked")
    public static <T> AttributeKey<T> valueOf(String name) {
        return (AttributeKey<T>) POOL.valueOf(name);
    }

    /**
     * @param name name
     * @return 是否存在
     */
    public static boolean exists(String name) {
        return POOL.exists(name);
    }


    /**
     * @param name name
     * @return value
     * @param <T> T
     */
    @SuppressWarnings("unchecked")
    public static <T> AttributeKey<T> newInstance(String name) {
        return (AttributeKey<T>) POOL.newInstance(name);
    }

    /**
     * @param firstNameComponent first
     * @param secondNameComponent second
     * @return value
     * @param <T> T
     */
    @SuppressWarnings("unchecked")
    public static <T> AttributeKey<T> valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return (AttributeKey<T>) POOL.valueOf(firstNameComponent, secondNameComponent);
    }

    private AttributeKey(int id, String name) {
        super(id, name);
    }
}
