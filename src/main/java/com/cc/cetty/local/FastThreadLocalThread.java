package com.cc.cetty.local;

/**
 * 该线程和原生的thread其实没什么大的不同
 * Netty的线程可以对传进来的runnable包装一下
 * 然后配合FastThreadLocal返回一个InternalThreadLocalMap。
 *
 * @author: cc
 * @date: 2023/11/06
 **/
public class FastThreadLocalThread extends Thread {

    private InternalThreadLocalMap threadLocalMap;

    public FastThreadLocalThread() {
    }

    public FastThreadLocalThread(Runnable target) {
        super(FastThreadLocalRunnable.wrap(target));
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target) {
        super(group, FastThreadLocalRunnable.wrap(target));
    }

    public FastThreadLocalThread(String name) {
        super(name);
    }

    public FastThreadLocalThread(ThreadGroup group, String name) {
        super(group, name);
    }

    public FastThreadLocalThread(Runnable target, String name) {
        super(FastThreadLocalRunnable.wrap(target), name);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target, String name) {
        super(group, FastThreadLocalRunnable.wrap(target), name);
    }

    public FastThreadLocalThread(ThreadGroup group, Runnable target, String name, long stackSize) {
        super(group, FastThreadLocalRunnable.wrap(target), name, stackSize);
    }

    public final InternalThreadLocalMap threadLocalMap() {
        return threadLocalMap;
    }

    public final void setThreadLocalMap(InternalThreadLocalMap threadLocalMap) {
        this.threadLocalMap = threadLocalMap;
    }

}

