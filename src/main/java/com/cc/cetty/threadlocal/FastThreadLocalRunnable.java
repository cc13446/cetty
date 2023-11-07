package com.cc.cetty.threadlocal;

import com.cc.cetty.utils.AssertUtils;

/**
 * 对Runnable做了一个包装，目的是让使用FastThreadLocalThread的线程执行完毕后，可以自动删除FastThreadLocalMap中的数据
 *
 * @author: cc
 * @date: 2023/11/06
 **/
public final class FastThreadLocalRunnable implements Runnable {
    private final Runnable runnable;

    private FastThreadLocalRunnable(Runnable runnable) {
        this.runnable = AssertUtils.checkNotNull(runnable);
    }

    @Override
    public void run() {
        try {
            runnable.run();
        } finally {
            //删除就体现在这里，线程退出的时候肯定会执行该方法
            FastThreadLocal.removeAll();
        }
    }

    public static Runnable wrap(Runnable runnable) {
        return runnable instanceof FastThreadLocalRunnable ? runnable : new FastThreadLocalRunnable(runnable);
    }
}
