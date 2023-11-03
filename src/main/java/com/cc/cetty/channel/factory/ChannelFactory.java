package com.cc.cetty.channel.factory;

import com.cc.cetty.channel.Channel;

/**
 * @author: cc
 * @date: 2023/11/3
 */
public interface ChannelFactory<T extends Channel> {

    T newChannel();
}

