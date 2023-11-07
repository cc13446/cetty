package com.cc.cetty.channel.exception;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public class ChannelException extends RuntimeException{
    private static final long serialVersionUID = 2908618315971075004L;

    public ChannelException() {
    }


    public ChannelException(String message, Throwable cause) {
        super(message, cause);
    }


    public ChannelException(String message) {
        super(message);
    }


    public ChannelException(Throwable cause) {
        super(cause);
    }

    protected ChannelException(String message, Throwable cause, boolean shared) {
        super(message, cause, false, true);
        assert shared;
    }

    static ChannelException newStatic(String message, Throwable cause) {
        return new ChannelException(message, cause, true);

    }
}
