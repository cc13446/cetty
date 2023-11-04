package com.cc.cetty.bootstrap.config;

import com.cc.cetty.bootstrap.Bootstrap;
import com.cc.cetty.channel.Channel;

import java.net.SocketAddress;
import java.util.Objects;

/**
 * @author: cc
 * @date: 2023/11/4
 */

public final class BootstrapConfig extends AbstractBootstrapConfig<Bootstrap, Channel> {

    public BootstrapConfig(Bootstrap bootstrap) {
        super(bootstrap);
    }

    public SocketAddress remoteAddress() {
        return bootstrap.remoteAddress();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(super.toString());
        buf.setLength(buf.length() - 1);
        buf.append(", resolver: ");
        SocketAddress remoteAddress = remoteAddress();
        if (Objects.nonNull(remoteAddress)) {
            buf.append(", remoteAddress: ").append(remoteAddress);
        }
        return buf.append(')').toString();
    }
}
