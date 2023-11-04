package com.cc.cetty.bootstrap.config;

import com.cc.cetty.bootstrap.ServerBootstrap;
import com.cc.cetty.channel.Channel;
import com.cc.cetty.event.loop.EventLoopGroup;

/**
 * @author: cc
 * @date: 2023/11/4
 */
public final class ServerBootstrapConfig extends AbstractBootstrapConfig<ServerBootstrap, Channel> {

    public ServerBootstrapConfig(ServerBootstrap bootstrap) {
        super(bootstrap);
    }

    public EventLoopGroup childGroup() {
        return bootstrap.childGroup();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(super.toString());
        buf.setLength(buf.length() - 1);
        buf.append(", ");
        EventLoopGroup childGroup = childGroup();
        if (childGroup != null) {
            buf.append("childGroup: ");
            buf.append(childGroup.getClass().getSimpleName());
            buf.append(", ");
        }
        if (buf.charAt(buf.length() - 1) == '(') {
            buf.append(')');
        } else {
            buf.setCharAt(buf.length() - 2, ')');
            buf.setLength(buf.length() - 1);
        }

        return buf.toString();
    }
}
