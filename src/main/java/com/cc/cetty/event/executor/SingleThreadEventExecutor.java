package com.cc.cetty.event.executor;

import com.cc.cetty.async.future.Future;
import com.cc.cetty.async.promise.DefaultPromise;
import com.cc.cetty.async.promise.Promise;
import com.cc.cetty.event.executor.scheduler.ScheduledFutureTask;
import com.cc.cetty.event.reject.RejectedExecutionHandler;
import com.cc.cetty.threadlocal.FastThreadLocal;
import com.cc.cetty.utils.AssertUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * 单线程的事件执行器
 */
@Slf4j
public abstract class SingleThreadEventExecutor extends AbstractScheduledEventExecutor {

    /**
     * 执行器的初始状态，未启动
     */
    private static final int ST_NOT_STARTED = 1;

    /**
     * 执行器启动后的状态
     */
    private static final int ST_STARTED = 2;

    /**
     * 正在准备关闭的状态，这时候还没有关闭，一切正常运行
     */
    private static final int ST_SHUTTING_DOWN = 3;

    /**
     * 该状态下用户不能再提交任务了，但是单线程执行器还会执行剩下的任务
     */
    private static final int ST_SHUTDOWN = 4;

    /**
     * 单线程执行器真正关闭
     */
    private static final int ST_TERMINATED = 5;

    /**
     * 状态的更新器
     */
    private static final AtomicIntegerFieldUpdater<SingleThreadEventExecutor> STATE_UPDATER = AtomicIntegerFieldUpdater.newUpdater(SingleThreadEventExecutor.class, "state");

    /**
     * 状态
     */
    private volatile int state = ST_NOT_STARTED;

    /**
     * 优雅关闭静默期
     */
    private volatile long gracefulShutdownQuietPeriod;

    /**
     * 优雅关闭超时时间
     */
    private volatile long gracefulShutdownTimeout;

    /**
     * 优雅关闭开始时间
     */
    private long gracefulShutdownStartTime;

    /**
     * 任务队列
     */
    private final Queue<Runnable> taskQueue;

    /**
     * 线程
     */
    private volatile Thread thread;

    /**
     * 创建线程的执行器
     */
    private final Executor executor;

    /**
     * 添加任务时是否唤醒
     */
    private final boolean addTaskWakesUp;

    /**
     * 是否被打断
     */
    private volatile boolean interrupted;

    /**
     * 判断线程是否启动
     */
    private final CountDownLatch threadLock = new CountDownLatch(1);

    /**
     * 这个成员变量很有意思，用户可以自己定义一些任务
     * 当单线程执行器停止运行的时候，会执行这些任务，这里其实就是借鉴了jvm的钩子函数
     */
    private final Set<Runnable> shutdownHooks = new LinkedHashSet<>();

    /**
     * 拒绝策略
     */
    private final RejectedExecutionHandler rejectedExecutionHandler;

    /**
     * 最后一次运行的时间
     */
    private long lastExecutionTime;

    /**
     * 终止future
     */
    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);

    /**
     * 唤醒任务
     */
    private static final Runnable WAKEUP_TASK = () -> {
    };

    protected SingleThreadEventExecutor(EventExecutorGroup parent, Executor executor, boolean addTaskWakesUp, Queue<Runnable> taskQueue, RejectedExecutionHandler rejectedHandler) {
        super(parent);
        this.addTaskWakesUp = addTaskWakesUp;
        this.executor = executor;
        this.taskQueue = AssertUtils.checkNotNull(taskQueue, "taskQueue");
        this.rejectedExecutionHandler = AssertUtils.checkNotNull(rejectedHandler);
    }

    /**
     * 该方法在NioEventLoop中实现，是真正执行轮询的方法
     */
    protected abstract void run();

    /**
     * 执行器执行任务
     */
    @Override
    public void execute(Runnable task) {
        AssertUtils.checkNotNull(task);
        boolean inEventLoop = inEventLoop(Thread.currentThread());
        addTask(task);
        // 自己不能给自己提交任务
        if (!inEventLoop) {
            // 启动单线程执行器
            startThread();
            // 如果单线程执行器的state >= ST_SHUTDOWN，这时候就意味着用户不能再提交任务了
            if (isShutdown()) {
                // 刚才添加了什么任务，这里就删除
                // 因为添加任务，也就是 addTask(task)方法内部是没有状态限制的
                // 所以添加了要再删除
                if (removeTask(task)) {
                    // 这里抛出异常就是通知用户别再提交任务了
                    // 可以看到，这里Netty其实没有设置特别强硬的拒绝任务的手段
                    reject();
                }
            }
        }
        // 这里就是单线程执行器还在正常运转的状态
        // 所以添加任务后要唤醒selector，防止线程阻塞，不能执行异步任务
        if (!addTaskWakesUp && wakesUpForTask(task)) {
            wakeup(inEventLoop);
        }
    }

    /**
     * 添加任务
     *
     * @param task task
     */
    private void addTask(Runnable task) {
        AssertUtils.checkNotNull(task);
        //如果添加失败，执行拒绝策略
        if (!offerTask(task)) {
            reject(task);
        }
    }

    /**
     * 添加任务
     *
     * @param task task
     */
    private boolean offerTask(Runnable task) {
        return taskQueue.offer(task);
    }

    /**
     * 拒绝任务
     *
     * @param task task
     */
    private void reject(Runnable task) {
        rejectedExecutionHandler.rejected(task, this);
    }

    /**
     * 移除任务
     *
     * @param task task
     * @return 是否成功
     */
    protected boolean removeTask(Runnable task) {
        AssertUtils.checkNotNull(task);
        return taskQueue.remove(task);
    }

    /**
     * 开始线程
     */
    private void startThread() {
        // 判断线程状态是不是还未启动的状态
        if (state == ST_NOT_STARTED) {
            // cas改变状态
            if (STATE_UPDATER.compareAndSet(this, ST_NOT_STARTED, ST_STARTED)) {
                boolean success = false;
                try {
                    // 启动线程
                    doStartThread();
                    success = true;
                } finally {
                    if (!success) {
                        // 没成功则重置状态
                        STATE_UPDATER.compareAndSet(this, ST_STARTED, ST_NOT_STARTED);
                    }
                }
            }
        }
    }

    /**
     * 启动线程
     */
    private void doStartThread() {
        assert thread == null;
        executor.execute(() -> {
            thread = Thread.currentThread();
            if (interrupted) {
                thread.interrupt();
            }
            boolean success = false;
            updateLastExecutionTime();
            try {
                // 真正的循环，也就是单线程执行器启动后要处理的IO时间
                SingleThreadEventExecutor.this.run();
                // 如果走到这里，意味着循环结束了，也就意味着单线程执行器停止了
                success = true;
            } catch (Throwable t) {
                log.warn("Unexpected exception from an event executor: ", t);
            } finally {
                // 走到这里意味着单线程执行器结束循环了，并且任务队列中的任务和ShutdownHook也都执行完了
                // 具体逻辑可以看NioEventLoop类中run方法的逻辑
                // 这里就会最后对单线程执行器的状态进行变更，最终变更到ST_TERMINATED状态
                for (; ; ) {
                    int oldState = state;
                    // 先判断状态，如果状态已经大于等于ST_SHUTTING_DOWN，就直接退出循环
                    // 如果前面返回为false，才会执行后面的代码，就把状态设置到大于等于ST_SHUTTING_DOWN
                    if (oldState >= ST_SHUTTING_DOWN || STATE_UPDATER.compareAndSet(SingleThreadEventExecutor.this, oldState, ST_SHUTTING_DOWN)) {
                        break;
                    }
                }
                // 这里是为了确认还没有执行confirmShutdown
                // 因为在confirmShutdown方法内，gracefulShutdownStartTime会被赋值的
                if (success && gracefulShutdownStartTime == 0) {
                    log.error("Buggy " + EventExecutor.class.getSimpleName() + " implementation; " + SingleThreadEventExecutor.class.getSimpleName() + ".confirmShutdown() must " + "be called before run() implementation terminates.");
                }
                try {
                    for (; ; ) {
                        // 这里最后一次执行这个方法，是因为这时候单线程执行器的状态还未改变，用户提交的任务还可以被执行
                        // 此时状态大于等于ST_SHUTTING_DOWN的状态，但绝对不是ST_TERMINATED，这个状态是绝对关闭状态
                        // 而处在3和4的状态，任务还是可以被执行的
                        if (confirmShutdown()) {
                            break;
                        }
                    }
                } finally {
                    // 清除单线程执行器的本地缓存
                    FastThreadLocal.removeAll();
                    // 单线程执行器的状态设置为终结状态
                    STATE_UPDATER.set(SingleThreadEventExecutor.this, ST_TERMINATED);
                    // 使得awaitTermination方法返回
                    threadLock.countDown();
                    if (log.isWarnEnabled() && !taskQueue.isEmpty()) {
                        // 这里会统计一下任务队列中有多少为执行的任务，然后打印一下
                        log.warn("An event executor terminated with non-empty task queue (" + taskQueue.size() + ')');
                    }
                    // 设置promise为成功状态，之前为这个promise添加的监听器就会回调了
                    // MultiThreadEventExecutorGroup中的terminationFuture就会被设置成功，用户就可以知道了
                    terminationFuture.setSuccess(null);
                }
            }
        });
    }

    /**
     * 更新最后运行时间
     */
    private void updateLastExecutionTime() {
        lastExecutionTime = ScheduledFutureTask.nanoTime();
    }

    /**
     * 确保关闭
     *
     * @return 是否成功
     */
    protected boolean confirmShutdown() {
        if (!isShuttingDown()) {
            return false;
        }
        if (!inEventLoop(Thread.currentThread())) {
            throw new IllegalStateException("must be invoked from an event loop");
        }
        // 首先把所有定时任务取消了
        cancelScheduledTasks();
        // 获得优雅释放资源的开始时间
        if (gracefulShutdownStartTime == 0) {
            gracefulShutdownStartTime = ScheduledFutureTask.nanoTime();
        }
        // 这里会执行所有任务
        // runShutdownHooks有点类似于jvm的钩子函数，都是程序退出时执行的，netty就借鉴过来了，改到释放资源时执行，因为这也相当于程序要退出了
        if (runAllTasks() || runShutdownHooks()) {
            if (isShutdown()) {
                // 判断状态是否关闭了
                // 为什么可以随时判断状态，这里要给大家再理一下思绪，因为我们是调用group的shutdownGracefully方法释放执行器的，这个方法是主线程在执行
                // 而现在这个方法是单线程执行器在执行，两个并不缠在一起，界限很清晰，所以要随时判断的
                return true;
            }
            //如果静默期为0，就可以直接退出了
            if (gracefulShutdownQuietPeriod == 0) {
                return true;
            }
            // 防止单线程执行器阻塞
            wakeup(true);
            return false;
        }
        // 获得当前的纳秒时间
        final long nanoTime = ScheduledFutureTask.nanoTime();
        // 判断是否超过了超时时间，如果超过直接关闭执行器
        if (isShutdown() || nanoTime - gracefulShutdownStartTime > gracefulShutdownTimeout) {
            return true;
        }
        // lastExecutionTime为上次任务的完成时间
        // 这里就是没有超过静默期呢，所以不能关闭单线程执行器
        if (nanoTime - lastExecutionTime <= gracefulShutdownQuietPeriod) {
            // Check if any tasks were added to the queue every 100ms.
            wakeup(true);
            try {
                // 每隔100ms检查一次，看在静默期中是否有任务要执行
                // 循环在外面
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Ignore
            }

            return false;
        }
        // 在静默期中没有任务要执行，直接结束即可
        return true;
    }

    /**
     * 超过超时时间，就会终止单线程执行器
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        AssertUtils.checkNotNull(unit);
        if (inEventLoop(Thread.currentThread())) {
            throw new IllegalStateException("cannot await termination of the current thread");
        }
        // 在这里阻塞住了
        threadLock.await(timeout, unit);
        return isTerminated();
    }

    @Override
    public boolean isShuttingDown() {
        return state >= ST_SHUTTING_DOWN;
    }

    @Override
    public boolean isShutdown() {
        return state >= ST_SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {
        return state == ST_TERMINATED;
    }

    /**
     * 这里就是关闭或者说优雅释放单线程执行器的真正方法
     */
    @Override
    public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
        if (quietPeriod < 0) {
            throw new IllegalArgumentException("quietPeriod: " + quietPeriod + " (expected >= 0)");
        }
        if (timeout < quietPeriod) {
            throw new IllegalArgumentException("timeout: " + timeout + " (expected >= quietPeriod (" + quietPeriod + "))");
        }
        if (unit == null) {
            throw new NullPointerException("unit");
        }
        if (isShuttingDown()) {
            return terminationFuture();
        }
        boolean inEventLoop = inEventLoop(Thread.currentThread());
        boolean wakeup;
        int oldState;
        for (; ; ) {
            if (isShuttingDown()) {
                return terminationFuture();
            }
            int newState;
            // 这个是为了唤醒selector，防止单线程执行器阻塞，一旦阻塞就执行不了剩下的任务了，一定要弄清楚，这个是在主线程中执行的
            wakeup = true;
            // 得到此时单线程执行器的状态
            oldState = state;
            if (inEventLoop) {
                newState = ST_SHUTTING_DOWN;
            } else {
                switch (oldState) {
                    case ST_NOT_STARTED:
                    case ST_STARTED:
                        // 如果是前两个就设置为第3个
                        newState = ST_SHUTTING_DOWN;
                        break;
                    default:
                        // 走到这里就意味着是第3或第4，没必要改变状态，因为状态的最终改变是在
                        // 单线程执行器的run方法中改变的
                        // 这里状态改变只是为了让IO事件的循环被打破，好执行到单线程执行器的run方法中
                        newState = oldState;
                        wakeup = false;
                }
            }
            // cas修改状态，就是把上面得到的状态设置一下，因为上面设置的是新状态，oldState还未被更改呢
            if (STATE_UPDATER.compareAndSet(this, oldState, newState)) {
                // 修改成功则退出循环
                break;
            }
        }
        // 静默期赋值，可以在confirmShutdown方法中使用
        gracefulShutdownQuietPeriod = unit.toNanos(quietPeriod);
        // 超时时间赋值，同样在confirmShutdown方法中使用
        gracefulShutdownTimeout = unit.toNanos(timeout);
        // 这保证单线程执行器还在运行，如果单线程执行器在上面的cas中状态修改成功，就可以直接退出了
        // 而不用再进行下面的代码
        if (ensureThreadStarted(oldState)) {
            return terminationFuture;
        }
        // 这里仍然是进行一次selector的唤醒，单线程执行器去执行任务队列中的任务，因为要关闭了
        // 这里要结合上面的switch分支来理解
        if (wakeup) {
            wakeup(inEventLoop);
        }
        return terminationFuture();
    }


    /**
     * 确保线程运行过
     *
     * @param oldState 旧状态
     * @return 线程运行成功
     */
    private boolean ensureThreadStarted(int oldState) {
        if (oldState == ST_NOT_STARTED) {
            try {
                doStartThread();
            } catch (Throwable cause) {
                STATE_UPDATER.set(this, ST_TERMINATED);
                terminationFuture.tryFailure(cause);
                if (!(cause instanceof Exception)) {
                    throw new RuntimeException(cause);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public Future<?> terminationFuture() {
        return terminationFuture;
    }

    /**
     * @param task 结束钩子
     */
    public void addShutdownHook(final Runnable task) {
        if (inEventLoop(Thread.currentThread())) {
            shutdownHooks.add(task);
        } else {
            execute(() -> shutdownHooks.add(task));
        }
    }

    /**
     * @param task 结束钩子
     */
    public void removeShutdownHook(final Runnable task) {
        if (inEventLoop(Thread.currentThread())) {
            shutdownHooks.remove(task);
        } else {
            execute(new Runnable() {
                @Override
                public void run() {
                    shutdownHooks.remove(task);
                }
            });
        }
    }

    /**
     * 运行结束钩子
     *
     * @return 是否成功
     */
    private boolean runShutdownHooks() {
        boolean ran = false;
        while (!shutdownHooks.isEmpty()) {
            List<Runnable> copy = new ArrayList<>(shutdownHooks);
            shutdownHooks.clear();
            for (Runnable task : copy) {
                try {
                    task.run();
                } catch (Throwable t) {
                    log.warn("Shutdown hook raised an exception.", t);
                } finally {
                    ran = true;
                }
            }
        }

        if (ran) {
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }

        return ran;
    }

    /**
     * 判断当前执行任务的线程是否是执行器的线程。这个方法至关重要，现在先有个印象
     */
    @Override
    public boolean inEventLoop(Thread thread) {
        return thread == this.thread;
    }

    /**
     *判断任务队列中是否有任务
     */
    protected boolean hasTasks() {
        return !taskQueue.isEmpty();
    }


    protected Runnable pollTask() {
        assert inEventLoop(Thread.currentThread());
        return pollTaskFrom(taskQueue);
    }

    protected static Runnable pollTaskFrom(Queue<Runnable> taskQueue) {
        for (; ; ) {
            Runnable task = taskQueue.poll();
            if (task == WAKEUP_TASK) {
                continue;
            }
            return task;
        }
    }


    private boolean fetchFromScheduledTaskQueue() {
        long nanoTime = AbstractScheduledEventExecutor.nanoTime();
        //从定时任务队列中取出即将到期执行的定时任务
        Runnable scheduledTask = pollScheduledTask(nanoTime);
        while (scheduledTask != null) {
            //把取出的定时任务方法普通任务队列中
            //当添加失败的时候，则把该任务重新放回定时任务队列中
            if (!taskQueue.offer(scheduledTask)) {
                scheduledTaskQueue().add((ScheduledFutureTask<?>) scheduledTask);
                return false;
            }
            scheduledTask = pollScheduledTask(nanoTime);
        }
        return true;
    }


    protected Runnable peekTask() {
        assert inEventLoop(Thread.currentThread());
        return taskQueue.peek();
    }


    public int pendingTasks() {
        return taskQueue.size();
    }

    /**
     * @Author: PP-jessica
     * @Description:执行所有任务
     */
    protected boolean runAllTasks() {
        assert inEventLoop(Thread.currentThread());
        boolean fetchedAll;
        boolean ranAtLeastOne = false;
        do {
            //把到期的定时任务从任务队列取出放到普通任务队列中
            fetchedAll = fetchFromScheduledTaskQueue();
            //执行任务队列中的任务，该方法返回true，则意味着至少执行了一个任务
            if (runAllTasksFrom(taskQueue)) {
                //给该变量赋值为true
                ranAtLeastOne = true;
            }
            //没有可执行的定时任务时，就退出该循环
        } while (!fetchedAll);
        if (ranAtLeastOne) {
            lastExecutionTime = ScheduledFutureTask.nanoTime();
        }
        return ranAtLeastOne;
    }

    protected final boolean runAllTasksFrom(Queue<Runnable> taskQueue) {
        //从普通任务队列中拉取异步任务
        Runnable task = pollTaskFrom(taskQueue);
        if (task == null) {
            return false;
        }
        for (; ; ) {
            //Reactor线程执行异步任务
            safeExecute(task);
            //执行完毕拉取下一个，如果是null，则直接返回
            task = pollTaskFrom(taskQueue);
            if (task == null) {
                return true;
            }
        }
    }

    /**
     * @Author: PP-jessica
     * @Description:这个是新添加的方法 传进来的参数就是执行用户提交的任务所限制的时间
     */
    protected boolean runAllTasks(long timeoutNanos) {
        //仍然是先通过下面这个方法，把定时任务添加到普通的任务队列中，这个方法会循环拉取
        //也就是说，可能会把很多定时任务拉取到普通任务队列中，直到无法拉取就结束
        fetchFromScheduledTaskQueue();
        //从普通的任务队列中获得第一个任务
        Runnable task = pollTask();
        //如果任务为null，直接退出即可
        if (task == null) {
            //注释掉就行
            //afterRunningAllTasks();
            return false;
        }
        //这里通过ScheduledFutureTask.nanoTime()方法计算出第一个定时任务开始执行到当前时间为止经过了多少时间
        //然后加上传进来的这个参数，也就是限制用户任务执行的时间，得到的其实就是一个执行用户任务的截止时间
        //也就是说，执行用户任务，只能执行到这个时间
        final long deadline = ScheduledFutureTask.nanoTime() + timeoutNanos;
        //这个变量记录已经执行了的任务的数量
        long runTasks = 0;
        //最后一次执行的时间
        long lastExecutionTime;
        //开始循环执行了
        for (; ; ) {
            //执行任务队列中的任务
            safeExecute(task);
            //执行任务数量加1
            runTasks++;
            //十六进制的0x3F其实就是十进制63
            //其二进制为111111，下面这里做&运算，如果等于0说明，runTasks的二进制的低6位都为0
            //而64的二进制为1000000，也就是说，只有当runTasks到达64的时候，下面这个判断条件就成立了
            //这里其实也是做了一个均衡的处理，就是判断看执行了64个用户提交的任务时，看看用户任务
            //的截止时间是否到了，如果到达截止时间，就退出循环。
            if ((runTasks & 0x3F) == 0) {
                //得到最后一次执行完的时间
                lastExecutionTime = ScheduledFutureTask.nanoTime();
                //这里判断是否超过限制的时间了
                if (lastExecutionTime >= deadline) {
                    //超过就退出循环，没超过就继续执行
                    break;
                }
            }
            //走到这里就是获取下一个任务
            task = pollTask();
            if (task == null) {
                //如果为null，并且到达截止时间，就退出循环
                lastExecutionTime = ScheduledFutureTask.nanoTime();
                break;
            }
        }
        //注释掉就行
        //afterRunningAllTasks();
        //给最后一次执行完的时间赋值
        this.lastExecutionTime = lastExecutionTime;
        return true;
    }

    /**
     * @Author: PP-jessica
     * @Description:新添加的方法
     */
    protected long delayNanos(long currentTimeNanos) {
        //得到第一个定时任务
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            return SCHEDULE_PURGE_INTERVAL;
        }
        //得到第一个定时任务的开始执行时间
        return scheduledTask.delayNanos(currentTimeNanos);
    }

    /**
     * @Author: PP-jessica
     * @Description:新添加的方法
     */
    @UnstableApi
    protected long deadlineNanos() {
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            return nanoTime() + SCHEDULE_PURGE_INTERVAL;
        }
        return scheduledTask.deadlineNanos();
    }

    /**
     * @Author: PP-jessica
     * @Description:新添加的方法
     */
    protected void wakeup(boolean inEventLoop) {
        if (!inEventLoop || state == ST_SHUTTING_DOWN) {
            taskQueue.offer(WAKEUP_TASK);
        }
    }

    private static final long SCHEDULE_PURGE_INTERVAL = TimeUnit.SECONDS.toNanos(1);

    protected static void reject() {
        throw new RejectedExecutionException("event executor terminated");
    }


    @SuppressWarnings("unused")
    protected boolean wakesUpForTask(Runnable task) {
        return true;
    }

    /**
     * @Author: PP-jessica
     * @Description: 中断单线程执行器中的线程
     */
    protected void interruptThread() {
        Thread currentThread = thread;
        if (currentThread == null) {
            interrupted = true;
        } else {
            currentThread.interrupt();
        }
    }

}
