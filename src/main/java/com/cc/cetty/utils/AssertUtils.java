package com.cc.cetty.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * @author: cc
 * @date: 2023/11/2
 */
public class AssertUtils {

    /**
     * 检查是否为空
     *
     * @param v       v
     * @param message 信息
     * @param <V>     V
     * @return v
     */
    public static <V> V checkNotNull(V v, String message) {
        if (StringUtils.isBlank(message)) {
            message = v.getClass().getSimpleName() + " cannot be null";
        }
        assert Objects.nonNull(v) : message;
        return v;
    }

    /**
     * 检查是否为空
     *
     * @param v   v
     * @param <V> V
     * @return v
     */
    public static <V> V checkNotNull(V v) {
        return checkNotNull(v, null);
    }
}
