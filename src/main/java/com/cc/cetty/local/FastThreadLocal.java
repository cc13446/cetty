package com.cc.cetty.local;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Netty的数组下标是创建thread local时就确定的
 * 而Java原生的thread local则是通过hash值求数组下标
 *
 * @author: cc
 * @date: 2023/11/06
 **/
@Slf4j
public class FastThreadLocal<V> {

    /**
     * 在这个下标存放一个set集合，记录所有thread local，用来remove, 这里应该固定为0
     */
    private static final int variablesToRemoveIndex = InternalThreadLocalMap.nextVariableIndex();

    /**
     * 还记得FastThreadLocalRunnable这个类吗？removeAll方法就会在该类的run方法中被调用
     */
    @SuppressWarnings("unchecked")
    public static void removeAll() {
        // 得到存储数据的InternalThreadLocalMap
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getMap();
        if (Objects.isNull(threadLocalMap)) {
            return;
        }
        try {
            // 这里设计的很有意思，先通过fastThreadLocal的下标索引variablesToRemoveIndex，也就是0
            // 从存储数据的InternalThreadLocalMap中得到存储的value
            // 然后做了什么呢？
            // 判断value是否为空，不为空则把该value强转为一个set集合，再把集合转换成一个fastThreadLocal数组，遍历该数组
            // 然后通过fastThreadLocal删除threadLocalMap中存储的数据。
            // 这里可以看到，其实该线程引用到的每一个fastThreadLocal会组成set集合，然后被放到threadLocalMap数组的0号位置
            Object v = threadLocalMap.getIndexedVariable(variablesToRemoveIndex);
            if (Objects.nonNull(v) && v != InternalThreadLocalMap.UNSET) {
                Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
                FastThreadLocal<?>[] variablesToRemoveArray = variablesToRemove.toArray(new FastThreadLocal[0]);
                for (FastThreadLocal<?> tlv : variablesToRemoveArray) {
                    tlv.remove(threadLocalMap);
                }
            }
        } finally {
            // 这一步是为了删除InternalThreadLocalMap或者是SlowThreadLocalMap
            InternalThreadLocalMap.removeMap();
        }
    }

    /**
     * 得到 threadLocalMap 数组存储的元素个数
     *
     * @return size
     */
    public static int size() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getMap();
        if (Objects.isNull(threadLocalMap)) {
            return 0;
        } else {
            return threadLocalMap.size();
        }
    }

    /**
     * 该方法是把该线程引用的fastThreadLocal组成一个set集合，然后放到threadLocalMap数组的0号位置
     *
     * @param threadLocalMap thread local map
     * @param variable       value
     */
    @SuppressWarnings("unchecked")
    private static void recordVariablesToRemoveSet(InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {
        // 首先得到 threadLocalMap数组0号位置的对象
        Object v = threadLocalMap.getIndexedVariable(variablesToRemoveIndex);
        // 定义一个set集合
        Set<FastThreadLocal<?>> valueToRemove;
        if (v == InternalThreadLocalMap.UNSET || Objects.isNull(v)) {
            // 如果threadLocalMap的0号位置存储的数据为null，那就创建一个set集合
            valueToRemove = Collections.newSetFromMap(new IdentityHashMap<>());
            // 把InternalThreadLocalMap数组的0号位置设置成set集合
            threadLocalMap.setIndexedVariable(variablesToRemoveIndex, valueToRemove);
        } else {
            // 如果数组的0号位置不为null，就说明已经有set集合了，直接获得即可
            valueToRemove = (Set<FastThreadLocal<?>>) v;
        }
        // 把 fastThreadLocal 添加到set集合中
        valueToRemove.add(variable);
    }

    /**
     * 删除set集合中的某一个 fast thread local 对象
     *
     * @param threadLocalMap thread local map
     * @param variable       value
     */
    @SuppressWarnings("unchecked")
    private static void removeVariablesFromRemoveSet(InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {
        // 根据0下标获得set集合
        Object v = threadLocalMap.getIndexedVariable(variablesToRemoveIndex);
        if (v == InternalThreadLocalMap.UNSET || Objects.isNull(v)) {
            return;
        }
        Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
        variablesToRemove.remove(variable);
    }

    /**
     * 该属性就是决定了fastThreadLocal在threadLocalMap数组中的下标位置
     */
    private final int index;

    /**
     * FastThreadLocal构造器，创建的那一刻，threadLocal在map中的下标就已经确定了
     */
    public FastThreadLocal() {
        index = InternalThreadLocalMap.nextVariableIndex();
    }

    /**
     * 得到fastThreadLocal存储在map数组中的数据
     * 如果没有数据，调用用户自定义的初始化方法
     *
     * @return value
     */
    @SuppressWarnings("unchecked")
    public final V get() {
        // 得到存储数据的map
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getMap();
        Object v = threadLocalMap.getIndexedVariable(index);
        // 如果不为未设定状态就返回
        if (v != InternalThreadLocalMap.UNSET) {
            return (V) v;
        }
        // 返回该数据
        return initialize(threadLocalMap);
    }


    /**
     * 存在就返回，否则返回null
     *
     * @return value
     */
    @SuppressWarnings("unchecked")
    public final V getIfExists() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getMap();
        if (Objects.nonNull(threadLocalMap)) {
            Object v = threadLocalMap.getIndexedVariable(index);
            if (v != InternalThreadLocalMap.UNSET) {
                return (V) v;
            }
        }
        return null;
    }

    /**
     * 这是个初始化的方法，但并不是对于threadLocalMap初始化
     * 这个方法的意思是，如果我们还没有数据存储在threadLocalMap中，这时候就可以调用这个方法，
     * 在这个方法内进一步调用initialValue方法返回一个要存储的对象，再将它存储到map中
     * 而initialValue方法就是由用户自己实现的
     *
     * @param threadLocalMap thread local map
     * @return value
     */
    private V initialize(InternalThreadLocalMap threadLocalMap) {
        V v = null;
        try {
            // 该方法由用户自己实现
            v = initialValue();
        } catch (Exception e) {
            log.error("Init thread local value fail, Error:", e);
        }
        // 把创建好的对象存储到map中
        threadLocalMap.setIndexedVariable(index, v);
        recordVariablesToRemoveSet(threadLocalMap, this);
        return v;
    }

    /**
     * 把要存储的value设置到 thread local map 中
     *
     * @param value value
     */
    public final void set(V value) {
        // 如果该value不是未定义状态就可以直接存放
        if (value != InternalThreadLocalMap.UNSET) {
            // 得到该线程私有的 thread local map
            InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getMap();
            // 把值设置进去
            setKnownNotUnset(threadLocalMap, value);
        } else {
            remove();
        }
    }

    /**
     * 设置value到本地map中
     *
     * @param threadLocalMap thread local map
     * @param value          value
     */
    private void setKnownNotUnset(InternalThreadLocalMap threadLocalMap, V value) {
        // 设置value到本地map中
        if (threadLocalMap.setIndexedVariable(index, value)) {
            // 把fastThreadLocal对象放到本地map的0号位置的set中
            recordVariablesToRemoveSet(threadLocalMap, this);
        }
    }

    /**
     * @return thread local 是否有值
     */
    public final boolean isSet() {
        return isSet(InternalThreadLocalMap.getMap());
    }

    /**
     * @param threadLocalMap thread local map
     * @return thread local 是否有值
     */
    private boolean isSet(InternalThreadLocalMap threadLocalMap) {
        return Objects.nonNull(threadLocalMap) && threadLocalMap.isIndexedVariableSet(index);
    }

    /**
     * 移除 thread local
     */
    public final void remove() {
        remove(InternalThreadLocalMap.getMap());
    }

    /**
     * 删除InternalThreadLocalMap中的数据
     */
    @SuppressWarnings("unchecked")
    private void remove(InternalThreadLocalMap threadLocalMap) {
        if (Objects.isNull(threadLocalMap)) {
            return;
        }
        // 用fastThreadLocal的下标从map中得到存储的数据
        Object v = threadLocalMap.removeIndexedVariable(index);
        // 从map 0号位置的set中删除 fastThreadLocal对象
        removeVariablesFromRemoveSet(threadLocalMap, this);
        if (v != InternalThreadLocalMap.UNSET) {
            try {
                // 该方法可以由用户自己实现，可以对value做一些处理
                onRemoval((V) v);
            } catch (Exception e) {
                log.error("Thread local on removal fail, Error:", e);
            }
        }
    }

    /**
     * 该方法就是要被用户重写的初始化方法
     */
    protected V initialValue() throws Exception {
        return null;
    }

    /**
     * 该方法可以由用户自行定义扩展，在删除本地map中的数据时，可以扩展一些功能
     */
    protected void onRemoval(@SuppressWarnings("UnusedParameters") V value) throws Exception {
        // do something
    }
}
