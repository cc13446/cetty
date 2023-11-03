package com.cc.cetty.config.option;

import com.cc.cetty.config.constant.AbstractConstant;
import com.cc.cetty.config.constant.ConstantPool;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Objects;

/**
 * @author: cc
 * @date: 2023/11/3
 */
public class ChannelOption<T> extends AbstractConstant<ChannelOption<T>> {

    /**
     * Option 的常量池
     */
    private static final ConstantPool<ChannelOption<Object>> POOL = new ConstantPool<>() {
        @Override
        protected ChannelOption<Object> newConstant(int id, String name) {
            return new ChannelOption<>(id, name);
        }
    };

    /**
     * 常量
     *
     * @param name name
     * @param <T>  T
     * @return Option
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(String name) {
        return (ChannelOption<T>) POOL.valueOf(name);
    }

    /**
     * @param firstNameComponent  first
     * @param secondNameComponent second
     * @param <T>                 T
     * @return constant
     */
    @SuppressWarnings("unchecked")
    public static <T> ChannelOption<T> valueOf(Class<?> firstNameComponent, String secondNameComponent) {
        return (ChannelOption<T>) POOL.valueOf(firstNameComponent, secondNameComponent);
    }

    /**
     * @param name name
     * @return 是否存在此常量
     */
    public static boolean exists(String name) {
        return POOL.exists(name);
    }

    public static final ChannelOption<Integer> CONNECT_TIMEOUT_MILLIS = valueOf("CONNECT_TIMEOUT_MILLIS");
    public static final ChannelOption<Integer> WRITE_SPIN_COUNT = valueOf("WRITE_SPIN_COUNT");
    public static final ChannelOption<Boolean> ALLOW_HALF_CLOSURE = valueOf("ALLOW_HALF_CLOSURE");
    public static final ChannelOption<Boolean> AUTO_READ = valueOf("AUTO_READ");
    public static final ChannelOption<Boolean> AUTO_CLOSE = valueOf("AUTO_CLOSE");
    public static final ChannelOption<Boolean> SO_BROADCAST = valueOf("SO_BROADCAST");
    public static final ChannelOption<Boolean> SO_KEEPALIVE = valueOf("SO_KEEPALIVE");
    public static final ChannelOption<Integer> SO_SNDBUF = valueOf("SO_SNDBUF");
    public static final ChannelOption<Integer> SO_RCVBUF = valueOf("SO_RCVBUF");
    public static final ChannelOption<Boolean> SO_REUSEADDR = valueOf("SO_REUSEADDR");
    public static final ChannelOption<Integer> SO_LINGER = valueOf("SO_LINGER");
    public static final ChannelOption<Integer> SO_BACKLOG = valueOf("SO_BACKLOG");
    public static final ChannelOption<Integer> SO_TIMEOUT = valueOf("SO_TIMEOUT");
    public static final ChannelOption<Integer> IP_TOS = valueOf("IP_TOS");
    public static final ChannelOption<InetAddress> IP_MULTICAST_ADDR = valueOf("IP_MULTICAST_ADDR");
    public static final ChannelOption<NetworkInterface> IP_MULTICAST_IF = valueOf("IP_MULTICAST_IF");
    public static final ChannelOption<Integer> IP_MULTICAST_TTL = valueOf("IP_MULTICAST_TTL");
    public static final ChannelOption<Boolean> IP_MULTICAST_LOOP_DISABLED = valueOf("IP_MULTICAST_LOOP_DISABLED");
    public static final ChannelOption<Boolean> TCP_NODELAY = valueOf("TCP_NODELAY");
    public static final ChannelOption<Boolean> SINGLE_EVENTEXECUTOR_PER_GROUP = valueOf("SINGLE_EVENTEXECUTOR_PER_GROUP");

    protected ChannelOption(String name) {
        this(POOL.nextId(), name);
    }

    private ChannelOption(int id, String name) {
        super(id, name);
    }

    public void validate(T value) {
        if (Objects.isNull(value)) {
            throw new NullPointerException("value");
        }
    }
}
