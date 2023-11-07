package com.cc.cetty.handler.timeout.exception;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public final class ReadTimeoutException extends TimeoutException {

    private static final long serialVersionUID = 169287984113283421L;

    public static final ReadTimeoutException INSTANCE = new ReadTimeoutException(true);

    ReadTimeoutException() {
    }

    private ReadTimeoutException(boolean shared) {
        super(shared);
    }
}
