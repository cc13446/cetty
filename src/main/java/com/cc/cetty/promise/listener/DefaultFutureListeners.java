package com.cc.cetty.promise.listener;

import com.cc.cetty.promise.future.Future;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author: cc
 * @date: 2023/11/1
 */
public final class DefaultFutureListeners {

    private GenericFutureListener<? extends Future<?>>[] listeners;

    private int size;

    @SuppressWarnings("unchecked")
    public DefaultFutureListeners() {
        listeners = new GenericFutureListener[2];
        size = 0;
    }

    @SuppressWarnings("unchecked")
    public DefaultFutureListeners(GenericFutureListener<? extends Future<?>> listener) {
        listeners = new GenericFutureListener[2];
        listeners[0] = listener;
        size = 1;
    }

    @SafeVarargs
    public final void add(GenericFutureListener<? extends Future<?>>... newListeners) {
        GenericFutureListener<? extends Future<?>>[] listeners = this.listeners;
        final int size = this.size;

        int newSize = size;
        while (newSize < listeners.length + newListeners.length) {
            newSize = newSize << 1;
        }
        if (newSize != size) {
            this.listeners = listeners = Arrays.copyOf(listeners, newSize);
        }
        System.arraycopy(newListeners, 0, listeners, size, newListeners.length);
        this.size = size + newListeners.length;
    }

    @SafeVarargs
    public final void remove(GenericFutureListener<? extends Future<?>>... deleteListeners) {
        final GenericFutureListener<? extends Future<?>>[] listeners = this.listeners;
        int size = this.size;

        Set<GenericFutureListener<? extends Future<?>>> listenerSet = Arrays.stream(deleteListeners).collect(Collectors.toSet());

        for (int i = 0; i < size; i++) {
            if (listenerSet.contains(listeners[i])) {
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