package com.cc.cetty.handler.timeout.exception;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public final class WriteTimeoutException extends TimeoutException {

    private static final long serialVersionUID = -144786655770296065L;

    public static final WriteTimeoutException INSTANCE = new WriteTimeoutException(true);

    private WriteTimeoutException() {
    }

    private WriteTimeoutException(boolean shared) {
        super(shared);
    }
}
