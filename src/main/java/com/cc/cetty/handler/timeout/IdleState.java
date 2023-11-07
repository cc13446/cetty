package com.cc.cetty.handler.timeout;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public enum IdleState {
    /**
     * No data was received for a while.
     */
    READER_IDLE,
    /**
     * No data was sent for a while.
     */
    WRITER_IDLE,
    /**
     * No data was either received or sent for a while.
     */
    ALL_IDLE
}
