package com.cc.cetty.event.executor.chooser;

import com.cc.cetty.event.executor.EventExecutor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public final class DefaultEventExecutorChooserFactory implements EventExecutorChooserFactory {

    public static final DefaultEventExecutorChooserFactory INSTANCE = new DefaultEventExecutorChooserFactory();

    private DefaultEventExecutorChooserFactory() { }

    @Override
    public EventExecutorChooser newChooser(EventExecutor[] executors) {
        // 如果数组的长度是2的幂次方就返回PowerOfTwoEventExecutorChooser选择器
        if (isPowerOfTwo(executors.length)) {
            return new PowerOfTwoEventExecutorChooser(executors);
        } else {
            // 如果数组长度不是2的幂次方就返回通用选择器。其实看到2的幂次方，应该就可以想到作者考虑的是位运算
            return new GenericEventExecutorChooser(executors);
        }
    }

    private static boolean isPowerOfTwo(int val) {
        return (val & -val) == val;
    }

    private static final class PowerOfTwoEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();

        private final EventExecutor[] executors;

        PowerOfTwoEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            // idx初始化为0，之后每一次调用next方法，idx都会自增，这样经过运算后，得到的数组下标就会成为一个循环，执行器也就会被循环获取
            // 也就是轮询
            int index = idx.getAndIncrement() & executors.length - 1;
            return executors[index];
        }
    }

    private static final class GenericEventExecutorChooser implements EventExecutorChooser {
        private final AtomicInteger idx = new AtomicInteger();
        private final EventExecutor[] executors;

        GenericEventExecutorChooser(EventExecutor[] executors) {
            this.executors = executors;
        }

        @Override
        public EventExecutor next() {
            return executors[Math.abs(idx.getAndIncrement() % executors.length)];
        }
    }
}
