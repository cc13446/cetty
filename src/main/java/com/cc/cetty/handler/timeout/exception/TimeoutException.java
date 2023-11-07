package com.cc.cetty.handler.timeout.exception;

import com.cc.cetty.channel.exception.ChannelException;

/**
 * @author: cc
 * @date: 2023/11/06
 **/

public class TimeoutException extends ChannelException {

    private static final long serialVersionUID = 4673641882869672533L;

    TimeoutException() {
    }

    TimeoutException(boolean shared) {
        super(null, null, shared);
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
