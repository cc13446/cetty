package com.cc.cetty.timer;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public interface Timer {

    Timeout newTimeout(TimerTask task, long delay, TimeUnit unit);


    Set<Timeout> stop();
}
