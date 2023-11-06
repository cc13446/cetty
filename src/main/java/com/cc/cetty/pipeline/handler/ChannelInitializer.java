package com.cc.cetty.pipeline.handler;

import com.cc.cetty.channel.Channel;
import com.cc.cetty.pipeline.ChannelPipeline;
import com.cc.cetty.pipeline.annotation.Sharable;
import com.cc.cetty.pipeline.handler.context.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 首先这个类本身就是一个handler，这就意味着，一旦channel注册成功，该handler的handlerAdded方法就会被回调
 *
 * @author: cc
 * @date: 2023/11/6
 */
@Slf4j
@Sharable
public abstract class ChannelInitializer<C extends Channel> extends ChannelInboundHandlerAdapter {

    /**
     * 这个set集合是为了防止已经添加了该handler的ChannelPipeline再次被初始化
     * 主要用于服务端接受到的客户端的连接
     * 在服务端会有很多客户端的连接，也就有很多ChannelPipeline
     * 而ChannelInitializer类型的handler是被所有客户端channel共享的
     * 所以每初始化一次ChannelPipeline，向其中添加handler时，就要判断该类的handler有没有添加过，添加过就不再添加了
     */
    private final Set<ChannelHandlerContext> initMap = Collections.newSetFromMap(new ConcurrentHashMap<>());


    protected abstract void initChannel(C ch) throws Exception;

    @Override
    public final void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        if (initChannel(ctx)) {
            ctx.pipeline().fireChannelRegistered();
            removeState(ctx);
        } else {
            ctx.fireChannelRegistered();
        }
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.warn("Failed to initialize a channel. Closing: " + ctx.channel(), cause);
        ctx.close();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isRegistered()) {
            if (initChannel(ctx)) {
                removeState(ctx);
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        initMap.remove(ctx);
    }

    /**
     * @param ctx context
     * @return 是否还没初始化过
     * @throws Exception exception
     */
    @SuppressWarnings("unchecked")
    private boolean initChannel(ChannelHandlerContext ctx) throws Exception {
        if (initMap.add(ctx)) {
            try {
                // 调用initChannel抽象方法，该方法由用户实现
                initChannel((C) ctx.channel());
            } catch (Throwable cause) {
                exceptionCaught(ctx, cause);
            } finally {
                ChannelPipeline pipeline = ctx.pipeline();
                // 这里会发现只要该handler使用完毕，就会从ChannelPipeline中删除
                if (pipeline.context(this) != null) {
                    // 在该方法内，会对该handler的handlerRemoved方法进行回调
                    pipeline.remove(this);
                }
            }
            return true;
        }
        return false;
    }


    private void removeState(final ChannelHandlerContext ctx) {
        if (ctx.isRemoved()) {
            initMap.remove(ctx);
        } else {
            ctx.executor().execute(() -> initMap.remove(ctx));
        }
    }
}