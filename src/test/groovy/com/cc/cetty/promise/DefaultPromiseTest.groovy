package com.cc.cetty.promise


import com.cc.cetty.event.loop.nio.NioEventLoopGroup
import spock.lang.Specification

/**
 * @author: cc
 * @date: 2023/11/1 
 */
class DefaultPromiseTest extends Specification {

    def "default promise test"() {
        given:
        Promise<String> promise = new DefaultPromise<>(new NioEventLoopGroup(1).next())

        int res = 0
        promise.addListener(f -> res++)
        promise.addListener(f -> res++)

        def s = "result"

        when:
        promise.setSuccess(s)
        promise.addListener(f -> res++)

        Thread.sleep(10000)

        then:
        s == promise.get()
        res == 3
    }
}
