package com.cc.cetty.local

import groovy.util.logging.Slf4j
import spock.lang.Specification


/**
 * @author cc
 * @date 2023/11/20
 */
@Slf4j
class FastThreadLocalTest extends Specification {

    def "test fast thread local"() {
        given:
        FastThreadLocal<Integer> fastThreadLocal = new FastThreadLocal<>()
        ThreadLocal<Integer> threadLocal = new ThreadLocal<>()

        when:
        long fastRes = System.currentTimeMillis()
        Thread fastThread = FastThreadLocalThread.start(() -> {
            fastThreadLocal.set(0)
            while (fastThreadLocal.get() < 10000) {
                fastThreadLocal.set(fastThreadLocal.get() + 1)
            }
            fastRes = System.currentTimeMillis() - fastRes
        })

        long slowRes = System.currentTimeMillis()
        Thread slowThread = Thread.start(() -> {
            threadLocal.set(0)
            while (threadLocal.get() < 10000) {
                threadLocal.set(threadLocal.get() + 1)
            }
            slowRes = System.currentTimeMillis() - slowRes
        })

        and:
        fastThread.join()
        slowThread.join()

        log.info("Fast thread local cost : {} ms", fastRes)
        log.info("Slow thread local cost : {} ms", slowRes)

        then:
        fastRes > slowRes
    }
}
