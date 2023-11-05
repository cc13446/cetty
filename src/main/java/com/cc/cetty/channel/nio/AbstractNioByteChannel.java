package com.cc.cetty.channel.nio;

import com.cc.cetty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * @author: cc
 * @date: 2023/11/2
 */
@Slf4j
public abstract class AbstractNioByteChannel extends AbstractNioChannel {

    protected AbstractNioByteChannel(Channel parent, SelectableChannel ch) {
        super(parent, ch, SelectionKey.OP_READ);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioByteUnsafe();
    }

    protected class NioByteUnsafe extends AbstractNioUnsafe {

        @Override
        public final void read() {
            ByteBuffer byteBuf = ByteBuffer.allocate(1024);
            try {
                doReadBytes(byteBuf);
            } catch (Exception e) {
                log.error("Fail to beginRead", e);
            }
        }
    }

    protected abstract int doReadBytes(ByteBuffer buf) throws Exception;
}
