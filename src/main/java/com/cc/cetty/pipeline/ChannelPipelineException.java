package com.cc.cetty.pipeline;

/**
 * @author: cc
 * @date: 2023/11/05
 **/
public class ChannelPipelineException extends RuntimeException{

    private static final long serialVersionUID = 3379174210419885980L;

    public ChannelPipelineException() {
    }

    public ChannelPipelineException(String message, Throwable cause) {
        super(message, cause);
    }

    public ChannelPipelineException(String message) {
        super(message);
    }

    public ChannelPipelineException(Throwable cause) {
        super(cause);
    }
}
