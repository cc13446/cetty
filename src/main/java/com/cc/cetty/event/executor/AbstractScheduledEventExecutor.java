package com.cc.cetty.event.executor;

import com.cc.cetty.event.executor.scheduler.ScheduledFuture;
import com.cc.cetty.event.executor.scheduler.ScheduledFutureTask;
import com.cc.cetty.priority.DefaultPriorityQueue;
import com.cc.cetty.priority.PriorityQueue;
import com.cc.cetty.utils.AssertUtils;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Comparator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author: cc
 * @date: 2023/11/07
 **/
public abstract class AbstractScheduledEventExecutor extends AbstractEventExecutor {

    /**
     * 该成员变量是一个比较器，通过task的到期事件比较大小。谁的到期时间长谁就大
     */
    private static final Comparator<ScheduledFutureTask<?>> SCHEDULED_FUTURE_TASK_COMPARATOR = ScheduledFutureTask::compareTo;

    /**
     * @return 当前启动时间 ns
     */
    protected static long nanoTime() {
        return ScheduledFutureTask.nanoTime();
    }

    /**
     * 定时任务队列
     */
    protected PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue;

    protected AbstractScheduledEventExecutor() {
    }

    protected AbstractScheduledEventExecutor(EventExecutorGroup parent) {
        super(parent);
    }

    /**
     * 得到存储定时任务的任务队列，可以看到其实现实际上是一个优先级队列
     */
    protected PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue() {
        if (Objects.isNull(scheduledTaskQueue)) {
            // 这里把定义好的比较器SCHEDULED_FUTURE_TASK_COMPARATOR传进去了
            scheduledTaskQueue = new DefaultPriorityQueue<>(SCHEDULED_FUTURE_TASK_COMPARATOR, 11);
        }
        return scheduledTaskQueue;
    }

    /**
     * 取消任务队列中的所有任务
     */
    protected void cancelScheduledTasks() {
        assert inEventLoop(Thread.currentThread());
        // 得到任务队列
        PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (CollectionUtils.isEmpty(scheduledTaskQueue)) {
            return;
        }
        // 把任务队列转换成数组
        final ScheduledFutureTask<?>[] scheduledTasks = scheduledTaskQueue.toArray(new ScheduledFutureTask<?>[0]);
        // 依次取消任务，该方法最终回调用到promise中
        for (ScheduledFutureTask<?> task : scheduledTasks) {
            task.cancelWithoutRemove(false);
        }
        // 清空数组，实际上只是把size置为0了
        scheduledTaskQueue.clearIgnoringIndexes();
    }

    /**
     * 拉取定时任务
     *
     * @return 定时任务
     */
    protected final Runnable pollScheduledTask() {
        return pollScheduledTask(nanoTime());
    }

    /**
     * 该方法用来获取即将可以执行的定时任务
     */
    protected final Runnable pollScheduledTask(long nanoTime) {
        assert inEventLoop(Thread.currentThread());
        // 得到任务队列
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        // 从任务队列中取出首元素
        ScheduledFutureTask<?> scheduledTask = Objects.isNull(scheduledTaskQueue) ? null : scheduledTaskQueue.peek();
        if (Objects.isNull(scheduledTask)) {
            return null;
        }
        // 如果首任务符合被执行的条件，就将该任务返回
        if (scheduledTask.deadlineNanos() <= nanoTime) {
            scheduledTaskQueue.remove();
            return scheduledTask;
        }
        return null;
    }

    /**
     * 距离下一个任务执行的时间
     */
    protected final long nextScheduledTaskNano() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        // 获取任务队列的头元素
        ScheduledFutureTask<?> scheduledTask = Objects.isNull(scheduledTaskQueue) ? null : scheduledTaskQueue.peek();
        if (Objects.isNull(scheduledTask)) {
            return -1;
        }
        // 用该任务的到期时间减去当前事件
        return Math.max(0, scheduledTask.deadlineNanos() - nanoTime());
    }

    /**
     * @return 当前的最近任务
     */
    protected final ScheduledFutureTask<?> peekScheduledTask() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (Objects.isNull(scheduledTaskQueue)) {
            return null;
        }
        // 获取头部元素
        return scheduledTaskQueue.peek();
    }

    /**
     * 该方法会在NioEventLoop中被调用，用来判断是否存在已经到期了的定时任务。实际上就是得到定时任务队列中的首任务 判断其是否可以被执行了
     */
    protected final boolean hasScheduledTasks() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        ScheduledFutureTask<?> scheduledTask = Objects.isNull(scheduledTaskQueue) ? null : scheduledTaskQueue.peek();
        return Objects.nonNull(scheduledTask) && scheduledTask.deadlineNanos() <= nanoTime();
    }

    /**
     * 提交普通的定时任务到任务队列
     */
    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        AssertUtils.checkNotNull(command);
        AssertUtils.checkNotNull(unit);
        if (delay < 0) {
            delay = 0;
        }
        return schedule(new ScheduledFutureTask<Void>(this, command, null, ScheduledFutureTask.deadlineNanos(unit.toNanos(delay))));
    }

    /**
     * 提交普通的定时任务到任务队列
     */
    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        AssertUtils.checkNotNull(callable);
        AssertUtils.checkNotNull(unit);
        if (delay < 0) {
            delay = 0;
        }
        return schedule(new ScheduledFutureTask<V>(this, callable, ScheduledFutureTask.deadlineNanos(unit.toNanos(delay))));
    }

    /**
     * 等待上一个执行完后才继续执行下一个
     */
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        AssertUtils.checkNotNull(command);
        AssertUtils.checkNotNull(unit);
        if (initialDelay < 0) {
            throw new IllegalArgumentException(String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (period <= 0) {
            throw new IllegalArgumentException(String.format("period: %d (expected: > 0)", period));
        }
        // 在这里提交定时任务致任务队列
        return schedule(new ScheduledFutureTask<Void>(this, Executors.callable(command, null), ScheduledFutureTask.deadlineNanos(unit.toNanos(initialDelay)), unit.toNanos(period)));
    }

    /**
     * 方法会到了固定时间，不管上一个方法有没有执行完，都会立即执行下一个方法
     */
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        AssertUtils.checkNotNull(command);
        AssertUtils.checkNotNull(unit);
        if (initialDelay < 0) {
            throw new IllegalArgumentException(String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (delay <= 0) {
            throw new IllegalArgumentException(String.format("delay: %d (expected: > 0)", delay));
        }

        return schedule(new ScheduledFutureTask<Void>(this, Executors.callable(command, null), ScheduledFutureTask.deadlineNanos(unit.toNanos(initialDelay)), -unit.toNanos(delay)));
    }

    /**
     * 向定时任务队列中添加任务
     */
    <V> ScheduledFuture<V> schedule(final ScheduledFutureTask<V> task) {
        if (inEventLoop(Thread.currentThread())) {
            scheduledTaskQueue().add(task);
        } else {
            execute(() -> scheduledTaskQueue().add(task));
        }
        return task;
    }

    /**
     * 从任务队列中移除一个任务
     */
    final void removeScheduled(final ScheduledFutureTask<?> task) {
        if (inEventLoop(Thread.currentThread())) {
            scheduledTaskQueue().removeTyped(task);
        } else {
            execute(() -> removeScheduled(task));
        }
    }
}
