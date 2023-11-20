package com.cc.cetty.local;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 该类虽然名为map
 * 实际上是一个数组
 * 这个数组配合fastThreadLocal使用
 *
 * @author: cc
 * @date: 2023/11/06
 **/
@Slf4j
public final class InternalThreadLocalMap {

    /**
     * 如果使用的线程不是fast thread local thread，那就返回一个原生的ThreadLocal，原生的ThreadLocal可以得到原生的ThreadLocalMap
     */
    private static final ThreadLocal<InternalThreadLocalMap> slowThreadLocalMap = new ThreadLocal<>();

    /**
     * FastThreadLocal的索引，每个FastThreadLocal都会有一个索引，也就是要存放到数组的下标位置
     * 该索引在FastThreadLocal创建的时候就初始化好了，是原子递增的
     */
    private static final AtomicInteger nextIndex = new AtomicInteger();

    /**
     * 未定义的一个对象，起这个名字是因为，一旦线程私有的map中删掉了一个value，那空出来的位置就会被该对象赋值
     */
    public static final Object UNSET = new Object();

    /**
     * 获取InternalThreadLocalMap
     *
     * @return thread local map
     */
    public static InternalThreadLocalMap getMap() {
        // 获得执行当前方法的线程
        Thread thread = Thread.currentThread();
        // 判断该线程是否为fast体系的线程
        // 只有被包装过的线程配合InternalThreadLocalMap才能发挥出高性能
        if (thread instanceof FastThreadLocalThread) {
            // 返回InternalThreadLocalMap
            return fastGetMap((FastThreadLocalThread) thread);
        } else {
            return slowGetMap();
        }
    }

    /**
     * 获取优化过的InternalThreadLocalMap
     *
     * @param thread thread
     * @return thread local map
     */
    private static InternalThreadLocalMap fastGetMap(FastThreadLocalThread thread) {
        InternalThreadLocalMap threadLocalMap = thread.threadLocalMap();
        if (Objects.isNull(threadLocalMap)) {
            thread.setThreadLocalMap(threadLocalMap = new InternalThreadLocalMap());
        }
        return threadLocalMap;
    }

    /**
     * 获取Java原生的本地map
     *
     * @return thread local map
     */
    private static InternalThreadLocalMap slowGetMap() {
        InternalThreadLocalMap result = slowThreadLocalMap.get();
        if (Objects.isNull(result)) {
            slowThreadLocalMap.set(result = new InternalThreadLocalMap());
        }
        return result;
    }

    /**
     * 把线程的私有map置为null
     */
    public static void removeMap() {
        Thread thread = Thread.currentThread();
        if (thread instanceof FastThreadLocalThread) {
            ((FastThreadLocalThread) thread).setThreadLocalMap(null);
        } else {
            slowThreadLocalMap.remove();
        }
    }

    /**
     * 该方法用来给fastThreadLocal的index赋值
     *
     * @return next index
     */
    public static int nextVariableIndex() {
        int index = nextIndex.getAndIncrement();
        if (index < 0) {
            nextIndex.decrementAndGet();
            throw new IllegalStateException("too many thread-local indexed variables");
        }
        return index;
    }

    /**
     * 初始化数组，该数组就是在map中存储数据用的
     */
    private static Object[] newIndexedVariableTable() {
        Object[] array = new Object[32];
        Arrays.fill(array, UNSET);
        return array;
    }

    /**
     * 真正存放数据的数组
     * 就是InternalThreadLocalMap存储数据的容器数组
     * 这时候要注意一个区别
     * 在原生ThreadLocalMap中，threadLocal会作为key存入到threadLocalMap中
     * 而在Netty中，fastThreadLocal只会提供一个数组下标的索引，并不会存入数组中，放进数组中的是对应的value值
     */
    private Object[] indexedVariables;

    private InternalThreadLocalMap() {
        this.indexedVariables = newIndexedVariableTable();
    }

    /**
     * 得到该map存储元素的个数
     */
    public int size() {
        int count = 0;
        for (Object o : indexedVariables) {
            if (o != UNSET) {
                count++;
            }
        }
        // 保留第一个元素用来保存threadLocal的列表
        // 后续移除全部元素时使用
        return count - 1;
    }

    /**
     * 取出数组内某个下标位置的元素
     */
    public Object getIndexedVariable(int index) {
        Object[] lookup = indexedVariables;
        return index < lookup.length ? lookup[index] : UNSET;
    }

    /**
     * 将数组内某个下标位置的数据替换为新的数据
     */
    public boolean setIndexedVariable(int index, Object value) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            Object oldValue = lookup[index];
            lookup[index] = value;
            return oldValue == UNSET;
        } else {
            // 数组扩容
            expandIndexedVariableTableAndSet(index, value);
            return true;
        }
    }

    /**
     * 数组扩容的方法，这里扩容的方法用的是某个fastThreadLocal的index。为什么要这样设置呢？
     * 大家可以思考一下，创建了fastThreadLocal就意味着数组的下标也就有了
     * 换句话说，如果创建了13个threadLocal，不管这几个threadLocal是否将其对应的value存储到了数组中，但是数组要存储的数据已经确定了
     * 如果有100多个threadLocal，那数组的下标就应该扩充到了100多
     * 当第100个threadLocal要把value存到数组中时，如果数组此时的容量为64，就要以index为基准进行扩容
     * 因为threadLocal已经创建到了100多个，这些threadLocal对应的value迟早是要存储到本地map中的
     * 所以，数组容量不够，就用传进来的index为基准，做位运算，得到一个2的幂次方的容量。
     *
     * @param index index
     * @param value value
     */
    private void expandIndexedVariableTableAndSet(int index, Object value) {
        Object[] oldArray = indexedVariables;
        final int oldCapacity = oldArray.length;
        int newCapacity = index;
        newCapacity |= newCapacity >>> 1;
        newCapacity |= newCapacity >>> 2;
        newCapacity |= newCapacity >>> 4;
        newCapacity |= newCapacity >>> 8;
        newCapacity |= newCapacity >>> 16;
        newCapacity++;
        // 扩容数组，把旧的数据拷贝新数组中
        Object[] newArray = Arrays.copyOf(oldArray, newCapacity);
        // 新数组扩容的那部分用UNSET赋值
        Arrays.fill(newArray, oldCapacity, newArray.length, UNSET);
        // 新数组的index下标的位置赋值为value
        newArray[index] = value;
        // 旧数组替换成新数组
        indexedVariables = newArray;
    }

    /**
     * 删除数组某个位置的元素，并且重新赋值为UNSET
     *
     * @param index index
     * @return removed value
     */
    public Object removeIndexedVariable(int index) {
        Object[] lookup = indexedVariables;
        if (index < lookup.length) {
            Object v = lookup[index];
            lookup[index] = UNSET;
            return v;
        } else {
            return UNSET;
        }
    }

    /**
     * @param index index
     * @return 是否设置了值
     */
    public boolean isIndexedVariableSet(int index) {
        Object[] lookup = indexedVariables;
        return index < lookup.length && lookup[index] != UNSET;
    }
}
