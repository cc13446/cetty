package com.cc.cetty.priority;

import java.util.Queue;

/**
 * @author: cc
 * @date: 2023/11/07
 **/
public interface PriorityQueue<T> extends Queue<T> {

    boolean removeTyped(T node);


    boolean containsTyped(T node);


    void priorityChanged(T node);


    void clearIgnoringIndexes();
}
