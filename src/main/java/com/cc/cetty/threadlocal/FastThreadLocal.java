package com.cc.cetty.threadlocal;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/**
 * Netty的数组下标是创建thread local时就确定的，而Java原生的thread local则是通过hash值求数组下标
 *
 * @author: cc
 * @date: 2023/11/06
 **/
@Slf4j
public class FastThreadLocal<V> {

    /**
     * 在这个下标存放一个set集合，用来remove
     */
    private static final int variablesToRemoveIndex = InternalThreadLocalMap.nextVariableIndex();

    /**
     * 还记得FastThreadLocalRunnable这个类吗？removeAll方法就会在该类的run方法中被调用
     */
    public static void removeAll() {
        // 得到存储数据的InternalThreadLocalMap
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (Objects.isNull(threadLocalMap)) {
            return;
        }
        try {
            // 这里设计的很有意思，先通过fast thread local的下标索引variablesToRemoveIndex，也就是0，
            // 从存储数据的InternalThreadLocalMap中得到存储的value
            // 然后做了什么呢？判断value是否为空，不为空则把该value赋值给一个set集合，再把集合转换成一个fast thread local数组，遍历该数组
            // 然后通过fast thread local删除thread local map中存储的数据。
            // 这里可以看到，其实该线程引用到的每一个fast thread local会组成set集合，然后被放到thread local map数组的0号位置
            Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
            if (Objects.nonNull(v) && v != InternalThreadLocalMap.UNSET) {
                @SuppressWarnings("unchecked")
                Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
                FastThreadLocal<?>[] variablesToRemoveArray = variablesToRemove.toArray(new FastThreadLocal[0]);
                for (FastThreadLocal<?> tlv : variablesToRemoveArray) {
                    tlv.remove(threadLocalMap);
                }
            }
        } finally {
            // 这一步是为了删除InternalThreadLocalMap或者是slowThreadLocalMap
            InternalThreadLocalMap.remove();
        }
    }

    /**
     * 得到 thread local map数组存储的元素个数
     */
    public static int size() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (Objects.isNull(threadLocalMap)) {
            return 0;
        } else {
            return threadLocalMap.size();
        }
    }

    /**
     * 销毁方法
     */
    public static void destroy() {
        InternalThreadLocalMap.destroy();
    }

    /**
     * 该方法是把该线程引用的fast thread local组成一个set集合，然后方到thread local map数组的0号位置
     */
    @SuppressWarnings("unchecked")
    private static void addToVariablesToRemove(InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {
        // 首先得到 thread local map数组0号位置的对象
        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
        // 定义一个set集合
        Set<FastThreadLocal<?>> variablesToRemove;
        if (v == InternalThreadLocalMap.UNSET || Objects.isNull(v)) {
            // 如果 thread local map的0号位置存储的数据为null，那就创建一个set集合
            variablesToRemove = Collections.newSetFromMap(new IdentityHashMap<>());
            // 把InternalThreadLocalMap数组的0号位置设置成set集合
            threadLocalMap.setIndexedVariable(variablesToRemoveIndex, variablesToRemove);
        } else {
            // 如果数组的0号位置不为null，就说明已经有set集合了，直接获得即可
            variablesToRemove = (Set<FastThreadLocal<?>>) v;
        }
        // 把 fast thread local 添加到set集合中
        variablesToRemove.add(variable);
    }

    /**
     * 删除set集合中的某一个 fast thread local 对象
     */
    private static void removeFromVariablesToRemove(InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {
        // 根据0下标获得set集合
        Object v = threadLocalMap.indexedVariable(variablesToRemoveIndex);
        if (v == InternalThreadLocalMap.UNSET || Objects.isNull(v)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
        variablesToRemove.remove(variable);
    }

    /**
     * 该属性就是决定了 fast thread local在thread local map数组中的下标位置
     */
    private final int index;

    /**
     * FastThreadLocal构造器，创建的那一刻，thread local在map中的下标就已经确定了
     */
    public FastThreadLocal() {
        index = InternalThreadLocalMap.nextVariableIndex();
    }

    /**
     * 得到 fast thread local存储在map数组中的数据
     */
    @SuppressWarnings("unchecked")
    public final V get() {
        // 得到存储数据的map
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
        Object v = threadLocalMap.indexedVariable(index);
        // 如果不为未设定状态就返回
        if (v != InternalThreadLocalMap.UNSET) {
            return (V) v;
        }
        // 返回该数据
        return initialize(threadLocalMap);
    }


    /**
     * 存在就返回，否则返回null
     */
    @SuppressWarnings("unchecked")
    public final V getIfExists() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (Objects.nonNull(threadLocalMap)) {
            Object v = threadLocalMap.indexedVariable(index);
            if (v != InternalThreadLocalMap.UNSET) {
                return (V) v;
            }
        }
        return null;
    }


    /**
     * 得到fast thread local存储在map数组中的数据，只不过这里把map当作参数纯进来了
     */
    @SuppressWarnings("unchecked")
    public final V get(InternalThreadLocalMap threadLocalMap) {
        Object v = threadLocalMap.indexedVariable(index);
        if (v != InternalThreadLocalMap.UNSET) {
            return (V) v;
        }
        return initialize(threadLocalMap);
    }

    /**
     * 这是个初始化的方法，但并不是对于threadLocalMap初始化
     * 这个方法的意思是，如果我们还没有数据存储在 thread local map 中，这时候就可以调用这个方法，
     * 在这个方法内进一步调用initialValue方法返回一个要存储的对象，再将它存储到map中
     * 而initialValue方法就是由用户自己实现的
     */
    private V initialize(InternalThreadLocalMap threadLocalMap) {
        V v = null;
        try {
            // 该方法由用户自己实现
            v = initialValue();
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        // 把创建好的对象存储到map中
        threadLocalMap.setIndexedVariable(index, v);
        addToVariablesToRemove(threadLocalMap, this);
        return v;
    }

    /**
     * 把要存储的value设置到 thread local map 中
     */
    public final void set(V value) {
        // 如果该value不是未定义状态就可以直接存放
        if (value != InternalThreadLocalMap.UNSET) {
            // 得到该线程私有的 thread local map
            InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
            //把值设置进去
            setKnownNotUnset(threadLocalMap, value);
        } else {
            remove();
        }
    }

    /**
     * 功能同上，就不再详细讲解了
     */
    public final void set(InternalThreadLocalMap threadLocalMap, V value) {
        if (value != InternalThreadLocalMap.UNSET) {
            setKnownNotUnset(threadLocalMap, value);
        } else {
            remove(threadLocalMap);
        }
    }

    /**
     * 设置value到本地map中
     */
    private void setKnownNotUnset(InternalThreadLocalMap threadLocalMap, V value) {
        // 设置value到本地map中
        if (threadLocalMap.setIndexedVariable(index, value)) {
            // 把fast thread local对象放到本地map的0号位置的set中
            addToVariablesToRemove(threadLocalMap, this);
        }
    }

    public final boolean isSet() {
        return isSet(InternalThreadLocalMap.getIfSet());
    }


    public final boolean isSet(InternalThreadLocalMap threadLocalMap) {
        return Objects.nonNull(threadLocalMap) && threadLocalMap.isIndexedVariableSet(index);
    }

    public final void remove() {
        remove(InternalThreadLocalMap.getIfSet());
    }


    /**
     * 删除InternalThreadLocalMap中的数据
     */
    @SuppressWarnings("unchecked")
    public final void remove(InternalThreadLocalMap threadLocalMap) {
        if (Objects.isNull(threadLocalMap)) {
            return;
        }
        // 用 fast thread local 的下标从map中得到存储的数据
        Object v = threadLocalMap.removeIndexedVariable(index);
        // 从map 0号位置的set中删除 fast thread local对象
        removeFromVariablesToRemove(threadLocalMap, this);
        if (v != InternalThreadLocalMap.UNSET) {
            try {
                //该方法可以由用户自己实现，可以对value做一些处理
                onRemoval((V) v);
            } catch (Exception e) {
                log.error(e.getMessage());
            }
        }
    }

    /**
     * 该方法就是要被用户重写的方法
     */
    protected V initialValue() throws Exception {
        return null;
    }

    /**
     * 该方法可以由用户自行定义扩展，在删除本地map中的数据时，可以扩展一些功能
     */
    protected void onRemoval(@SuppressWarnings("UnusedParameters") V value) throws Exception {
        // do nothing
    }
}
