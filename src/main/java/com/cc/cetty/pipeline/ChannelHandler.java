package com.cc.cetty.pipeline;

import com.cc.cetty.pipeline.annotation.Skip;

/**
 * @author: cc
 * @date: 2023/11/4
 */
public interface ChannelHandler {

    /**
     * 添加回调
     *
     * @param ctx context
     * @throws Exception exception
     */
    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * 移除回调
     *
     * @param ctx context
     * @throws Exception exception
     */
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;

    /**
     * 移除回调
     *
     * @param ctx   context
     * @param cause cause
     * @throws Exception exception
     */
    @Skip
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;
}