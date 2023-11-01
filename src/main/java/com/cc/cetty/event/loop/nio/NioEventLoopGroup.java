package com.cc.cetty.event.loop.nio;

import com.cc.cetty.event.loop.EventLoop;
import com.cc.cetty.event.loop.MultiThreadEventLoopGroup;

/**
 * nio 的事件循环组
 * @author: cc
 * @date: 2023/10/31
 */
public class NioEventLoopGroup extends MultiThreadEventLoopGroup {

    /**
     * @param threads 线程数
     */
    public NioEventLoopGroup(int threads) {
        super(threads);
    }

    @Override
    protected EventLoop newChild() {
        return new NioEventLoop();
    }
}
