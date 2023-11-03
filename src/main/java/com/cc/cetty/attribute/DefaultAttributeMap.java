package com.cc.cetty.attribute;

import com.cc.cetty.utils.AssertUtils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * @author: cc
 * @date: 2023/11/3
 */
public class DefaultAttributeMap implements AttributeMap {

    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultAttributeMap, AtomicReferenceArray> updater =
            AtomicReferenceFieldUpdater.newUpdater(DefaultAttributeMap.class, AtomicReferenceArray.class, "attributes");

    private static final int BUCKET_SIZE = 4;

    private static final int MASK = BUCKET_SIZE - 1;

    @SuppressWarnings("UnusedDeclaration")
    private volatile AtomicReferenceArray<DefaultAttribute<?>> attributes;

    @SuppressWarnings("unchecked")
    @Override
    public <T> Attribute<T> attr(AttributeKey<T> key) {
        AssertUtils.checkNotNull(key);
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        // 还未初始化过，初始化
        if (Objects.isNull(attributes)) {
            attributes = new AtomicReferenceArray<>(BUCKET_SIZE);
            // 用原子更新器把attributes更新成初始化好的数组
            // 这里要注意一下，虽然上面数组已经初始化好了，但初始化好的数组赋值给了局部变量
            // 到这里，才真正把初始化好的数组给到了对象中的attributes属性
            if (!updater.compareAndSet(this, null, attributes)) {
                attributes = this.attributes;
            }
        }
        // 计算数据在数组中存储的下标
        int i = index(key);
        // 可以类比向hashmap中添加数据的过程
        // 计算出下标后，先判断该下标上是否有数据
        DefaultAttribute<?> head = attributes.get(i);
        // 为null则说明暂时没有数据，可以直接添加，否则就要以链表的形式添加
        // 这里当然也不能忘记并发的情况，如果多个线程都洗向这个位置添加数据呢
        if (Objects.isNull(head)) {
            // 初始化一个头节点，但里面不存储任何数据
            head = new DefaultAttribute<>();
            // 创建一个DefaultAttribute对象，把头节点和key作为参数传进去
            // 要存储的value就存放在DefaultAttribute对象中，而DefaultAttribute对象存储在数组中
            DefaultAttribute<T> attr = new DefaultAttribute<>(head, key);
            head.next = attr;
            attr.prev = head;
            // 用cas给数组下标位置赋值，这里就应该想到并发问题，只要有一个线程原子添加成功就行
            if (attributes.compareAndSet(i, null, head)) {
                return attr;
            } else {
                // 说明该线程设置头节点失败，这时候就要把头节点重新赋值，因为其他线程已经把头节点添加进去了
                head = attributes.get(i);
            }
        }
        // 头节点已经初始化过了，说明要添加的位置已经有值，需要以链表的方法继续添加数据
        synchronized (head) {
            // 把当前节点赋值为头节点
            DefaultAttribute<?> curr = head;
            while (!Thread.interrupted()) {
                // 得到当前节点的下一个节点
                DefaultAttribute<?> next = curr.next;
                // 如果为null，说明当前节点就是最后一个节点
                if (Objects.isNull(next)) {
                    // 创建DefaultAttribute对象，封装数据
                    DefaultAttribute<T> attr = new DefaultAttribute<>(head, key);
                    curr.next = attr;
                    attr.prev = curr;
                    return attr;
                }
                // 如果下一个节点和传入的key相等，并且该节点并没有被删除，说明map中已经存在该数据了，直接返回该数据即可
                if (next.key == key && !next.removed) {
                    return (Attribute<T>) next;
                }
                // 把下一个节点赋值为当前节点
                curr = next;
            }
            throw new RuntimeException("Thread has been interrupted");
        }
    }

    @Override
    public <T> boolean hasAttr(AttributeKey<T> key) {
        AssertUtils.checkNotNull(key);
        AtomicReferenceArray<DefaultAttribute<?>> attributes = this.attributes;
        if (Objects.isNull(attributes)) {
            return false;
        }
        int i = index(key);
        DefaultAttribute<?> head = attributes.get(i);
        if (Objects.isNull(head)) {
            return false;
        }
        synchronized (head) {
            DefaultAttribute<?> cur = head.next;
            while (Objects.nonNull(cur)) {
                if (cur.key == key && !cur.removed) {
                    return true;
                }
                cur = cur.next;
            }
            return false;
        }
    }

    private static int index(AttributeKey<?> key) {
        return key.id() & MASK;
    }

    private static final class DefaultAttribute<T> extends AtomicReference<T> implements Attribute<T> {

        private static final long serialVersionUID = -2661411462200283011L;

        private final DefaultAttribute<?> head;

        private final AttributeKey<T> key;

        private DefaultAttribute<?> prev;

        private DefaultAttribute<?> next;

        private volatile boolean removed;

        DefaultAttribute(DefaultAttribute<?> head, AttributeKey<T> key) {
            this.head = head;
            this.key = key;
        }

        DefaultAttribute() {
            head = this;
            key = null;
        }

        @Override
        public AttributeKey<T> key() {
            return key;
        }

        @Override
        public T setIfAbsent(T value) {
            while (!compareAndSet(null, value)) {
                T old = get();
                if (old != null) {
                    return old;
                }
            }
            return null;
        }

        @Override
        public T getAndRemove() {
            removed = true;
            T oldValue = getAndSet(null);
            remove0();
            return oldValue;
        }

        @Override
        public void remove() {
            removed = true;
            set(null);
            // 删除一个节点，重排链表指针
            remove0();
        }

        private void remove0() {
            synchronized (head) {
                if (Objects.isNull(prev)) {
                    return;
                }
                prev.next = next;
                if (Objects.nonNull(next)) {
                    next.prev = prev;
                }
                prev = null;
                next = null;
            }
        }
    }
}
