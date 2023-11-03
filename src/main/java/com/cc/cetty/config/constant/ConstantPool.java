package com.cc.cetty.config.constant;

import com.cc.cetty.utils.AssertUtils;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 常量池，其实是个ConcurrentMap，以key-value的方式存储用户定义的配置信息
 *
 * @author: cc
 * @date: 2023/11/3
 */
public abstract class ConstantPool<T extends Constant<T>> {

    private final ConcurrentMap<String, T> constants = new ConcurrentHashMap<>();


    private final AtomicInteger nextId = new AtomicInteger(1);

    /**
     * @param firstNameComponent  first
     * @param secondNameComponent second
     * @return constant
     */
    public T valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        if (Objects.isNull(firstNameComponent)) {
            throw new NullPointerException("firstNameComponent");
        }
        if (Objects.isNull(secondNameComponent)) {
            throw new NullPointerException("secondNameComponent");
        }
        return valueOf(firstNameComponent.getName() + '#' + secondNameComponent);
    }

    /**
     * @param name name
     * @return constant
     */
    public T valueOf(String name) {
        checkNotBlank(name);
        return getOrCreate(name);
    }

    /**
     * @param name name
     * @return constant
     */
    private T getOrCreate(String name) {
        T constant = constants.get(name);
        if (Objects.isNull(constant)) {
            final T tempConstant = newConstant(nextId(), name);
            constant = constants.putIfAbsent(name, tempConstant);
            if ((Objects.isNull(constant))) {
                return tempConstant;
            }
        }
        return constant;
    }

    /**
     * @param name name
     * @return 常量是否存在
     */
    public boolean exists(String name) {
        checkNotBlank(name);
        return constants.containsKey(name);
    }


    /**
     * @param name name
     * @return constant
     */
    public T newInstance(String name) {
        checkNotBlank(name);
        return createOrThrow(name);
    }

    /**
     * 如果已存在就抛出异常
     *
     * @param name name
     * @return constant
     */
    private T createOrThrow(String name) {
        T constant = constants.get(name);
        if (Objects.isNull(constant)) {
            final T tempConstant = newConstant(nextId(), name);
            constant = constants.putIfAbsent(name, tempConstant);
            if (Objects.isNull(constant)) {
                return tempConstant;
            }
        }
        throw new IllegalArgumentException(String.format("'%s' is already in use", name));
    }

    /**
     * @param name name
     * @return name
     */
    private static String checkNotBlank(String name) {
        AssertUtils.checkNotBlank(name, "Name is blank");
        return name;
    }


    /**
     * @param id   id
     * @param name name
     * @return new constant
     */
    protected abstract T newConstant(int id, String name);

    /**
     * @return next id
     */
    public final int nextId() {
        return nextId.getAndIncrement();
    }
}
