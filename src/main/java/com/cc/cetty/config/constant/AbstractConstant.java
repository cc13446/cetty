package com.cc.cetty.config.constant;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author: cc
 * @date: 2023/11/3
 */
public abstract class AbstractConstant<T extends AbstractConstant<T>> implements Constant<T> {

    /**
     * 给每个常量一个递增的唯一long，作为区分
     */
    private static final AtomicLong uniqueIdGenerator = new AtomicLong();

    private final int id;
    private final String name;

    private final long uniquer;

    protected AbstractConstant(int id, String name) {
        this.id = id;
        this.name = name;
        this.uniquer = uniqueIdGenerator.getAndIncrement();
    }

    @Override
    public final String name() {
        return name;
    }

    @Override
    public final int id() {
        return id;
    }

    @Override
    public final String toString() {
        return name();
    }

    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    @Override
    public final int compareTo(T o) {
        if (this == o) {
            return 0;
        }
        AbstractConstant<T> other = o;
        int returnCode;

        returnCode = hashCode() - other.hashCode();
        if (returnCode != 0) {
            return returnCode;
        }

        if (uniquer < other.uniquer) {
            return -1;
        }
        if (uniquer > other.uniquer) {
            return 1;
        }

        throw new Error("failed to compare two different constants");
    }
}
