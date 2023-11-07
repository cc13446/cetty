package com.cc.cetty.event.executor.chooser;

import com.cc.cetty.event.executor.EventExecutor;

/**
 * @author: cc
 * @date: 2023/11/06
 **/
public interface EventExecutorChooserFactory {

    EventExecutorChooser newChooser(EventExecutor[] executors);

    interface EventExecutorChooser {
        /**
         * Returns the new {@link EventExecutor} to use.
         */
        EventExecutor next();
    }
}
