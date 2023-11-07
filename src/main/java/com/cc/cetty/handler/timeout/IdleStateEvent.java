package com.cc.cetty.handler.timeout;

import com.cc.cetty.utils.AssertUtils;
import lombok.Getter;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
@Getter
public class IdleStateEvent {
    public static final IdleStateEvent FIRST_READER_IDLE_STATE_EVENT = new IdleStateEvent(IdleState.READER_IDLE, true);
    public static final IdleStateEvent READER_IDLE_STATE_EVENT = new IdleStateEvent(IdleState.READER_IDLE, false);
    public static final IdleStateEvent FIRST_WRITER_IDLE_STATE_EVENT = new IdleStateEvent(IdleState.WRITER_IDLE, true);
    public static final IdleStateEvent WRITER_IDLE_STATE_EVENT = new IdleStateEvent(IdleState.WRITER_IDLE, false);
    public static final IdleStateEvent FIRST_ALL_IDLE_STATE_EVENT = new IdleStateEvent(IdleState.ALL_IDLE, true);
    public static final IdleStateEvent ALL_IDLE_STATE_EVENT = new IdleStateEvent(IdleState.ALL_IDLE, false);

    private final IdleState state;
    private final boolean first;

    protected IdleStateEvent(IdleState state, boolean first) {
        this.state = AssertUtils.checkNotNull(state);
        this.first = first;
    }

}