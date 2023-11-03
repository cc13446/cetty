package com.cc.cetty.config.option;

import java.io.IOException;
import java.net.SocketOption;
import java.nio.channels.Channel;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author: cc
 * @date: 2023/11/3
 */
public final class NioChannelOption<T> extends ChannelOption<T> {

    /**
     * 通过 socketOption 建 nio channel option
     * @param option option
     * @param <T> t
     * @return nio option
     */
    public static <T> ChannelOption<T> of(SocketOption<T> option) {
        return new NioChannelOption<>(option);
    }

    /**
     * 给 jdk channel 设置参数
     * @param jdkChannel jdk channel
     * @param option option
     * @param value v
     * @param <T> T
     * @return 是否设置成功
     */
    public static <T> boolean setOption(Channel jdkChannel, NioChannelOption<T> option, T value) {
        java.nio.channels.NetworkChannel channel = (java.nio.channels.NetworkChannel) jdkChannel;
        if (!channel.supportedOptions().contains(option.option)) {
            return false;
        }
        if (channel instanceof ServerSocketChannel && option.option == java.net.StandardSocketOptions.IP_TOS) {
            return false;
        }
        try {
            channel.setOption(option.option, value);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取 jdk channel 参数
     * @param jdkChannel jdk channel
     * @param option option
     * @param <T> T
     * @return value
     */
    public static <T> T getOption(Channel jdkChannel, NioChannelOption<T> option) {
        java.nio.channels.NetworkChannel channel = (java.nio.channels.NetworkChannel) jdkChannel;

        if (!channel.supportedOptions().contains(option.option)) {
            return null;
        }
        if (channel instanceof ServerSocketChannel && option.option == java.net.StandardSocketOptions.IP_TOS) {
            return null;
        }
        try {
            return channel.getOption(option.option);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取 jdk channel 参数
     * @param jdkChannel jdk channel
     * @return values
     */
    public static ChannelOption<?>[] getOptions(Channel jdkChannel) {
        java.nio.channels.NetworkChannel channel = (java.nio.channels.NetworkChannel) jdkChannel;
        Set<SocketOption<?>> supportedOpts = channel.supportedOptions();

        if (channel instanceof ServerSocketChannel) {
            List<ChannelOption<?>> extraOpts = new ArrayList<>(supportedOpts.size());
            for (SocketOption<?> opt : supportedOpts) {
                if (opt == java.net.StandardSocketOptions.IP_TOS) {
                    continue;
                }
                extraOpts.add(new NioChannelOption<>(opt));
            }
            return extraOpts.toArray(new ChannelOption[0]);
        } else {
            ChannelOption<?>[] extraOpts = new ChannelOption[supportedOpts.size()];

            int i = 0;
            for (SocketOption<?> opt : supportedOpts) {
                extraOpts[i++] = new NioChannelOption<>(opt);
            }
            return extraOpts;
        }
    }

    private final SocketOption<T> option;

    private NioChannelOption(SocketOption<T> option) {
        super(option.name());
        this.option = option;
    }

}
