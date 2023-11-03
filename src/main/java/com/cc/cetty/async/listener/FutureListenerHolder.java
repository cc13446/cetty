package com.cc.cetty.async.listener;

import com.cc.cetty.async.future.Future;

import java.util.Arrays;

/**
 * 监听器持有者
 *
 * @author: cc
 * @date: 2023/11/1
 */
public final class FutureListenerHolder {

    /**
     * 所有的监听器
     */
    private GenericFutureListener<? extends Future<?>>[] listeners;

    private int size;

    @SuppressWarnings("unchecked")
    public FutureListenerHolder() {
        listeners = new GenericFutureListener[2];
        size = 0;
    }

    public FutureListenerHolder(GenericFutureListener<? extends Future<?>> listener) {
        this();
        listeners[0] = listener;
        size = 1;
    }

    public void add(GenericFutureListener<? extends Future<?>> listener) {
        GenericFutureListener<? extends Future<?>>[] listeners = this.listeners;
        final int size = this.size;

        int newSize = size;
        while (newSize < listeners.length + 1) {
            newSize = newSize << 1;
        }

        if (newSize != size) {
            listeners = Arrays.copyOf(listeners, newSize);
        }

        listeners[size] = listener;

        this.listeners = listeners;
        this.size = size + 1;
    }

    public void remove(GenericFutureListener<? extends Future<?>> listener) {
        final GenericFutureListener<? extends Future<?>>[] listeners = this.listeners;
        int size = this.size;

        for (int i = 0; i < size; i++) {
            if (listeners[i] == listener) {
                int listenersToMove = size - i - 1;
                if (listenersToMove > 0) {
                    System.arraycopy(listeners, i + 1, listeners, i, listenersToMove);
                }
                listeners[--size] = null;
                this.size = size;
                return;
            }
        }
    }

    public GenericFutureListener<? extends Future<?>>[] listeners() {
        return listeners;
    }

    public int size() {
        return size;
    }

}