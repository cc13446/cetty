package com.cc.cetty.channel.nio;

import com.cc.cetty.channel.Channel;
import lombok.extern.slf4j.Slf4j;

import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author: cc
 * @date: 2023/11/2
 */
@Slf4j
public abstract class AbstractNioMessageChannel extends AbstractNioChannel {

    /**
     * 当该属性为true时，服务端将不再接受来自客户端的数据
     */
    boolean inputShutdown;

    protected AbstractNioMessageChannel(Channel parent, SelectableChannel ch, int readInterestOp) {
        super(parent, ch, readInterestOp);
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioMessageUnsafe();
    }

    @Override
    protected void doBeginRead() throws Exception {
        if (inputShutdown) {
            return;
        }
        super.doBeginRead();
    }

    private final class NioMessageUnsafe extends AbstractNioUnsafe {

        private final List<Object> readBuf = new ArrayList<>();

        @Override
        public void read() {
            assert eventLoop().inEventLoop(Thread.currentThread());
            Throwable exception = null;
            try {
                do {
                    int localRead = doReadMessages(readBuf);
                    if (localRead == 0) {
                        break;
                    }
                } while (true);
            } catch (Throwable t) {
                exception = t;
            }
            for (Object o : readBuf) {
                readPending = false;
                Channel child = (Channel) o;
                log.info("Received client channel");
            }
            readBuf.clear();
            if (Objects.nonNull(exception)) {
                throw new RuntimeException(exception);
            }
        }
    }

    /**
     * 这里的message指的是客户端 channel
     * @param buf buf
     * @return 客户端channel数量
     * @throws Exception exception
     */
    protected abstract int doReadMessages(List<Object> buf) throws Exception;
}
