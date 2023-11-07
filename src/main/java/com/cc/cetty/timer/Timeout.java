package com.cc.cetty.timer;

/**
 * @author: cc
 * @date: 2023/11/06
 **/

public interface Timeout {

    Timer timer();


    TimerTask task();


    boolean isExpired();


    boolean isCancelled();


    boolean cancel();
}

