package com.cc.cetty.timer;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public interface TimerTask {

    void run(Timeout timeout) throws Exception;
}

