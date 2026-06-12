package com.loopers.support

import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * threadCount개의 스레드가 동시에 task를 실행하도록 출발선(start latch)을 맞춘다.
 * task에서 던져진 예외를 모아 반환한다. (성공 수 = threadCount - 반환 리스트 크기)
 */
fun runConcurrently(threadCount: Int, task: (index: Int) -> Unit): List<Throwable> {
    val executor = Executors.newFixedThreadPool(threadCount)
    val ready = CountDownLatch(threadCount)
    val start = CountDownLatch(1)
    val done = CountDownLatch(threadCount)
    val errors = Collections.synchronizedList(mutableListOf<Throwable>())

    repeat(threadCount) { index ->
        executor.submit {
            ready.countDown()
            try {
                start.await()
                task(index)
            } catch (t: Throwable) {
                errors.add(t)
            } finally {
                done.countDown()
            }
        }
    }

    ready.await()
    start.countDown()
    done.await()
    executor.shutdown()
    return errors.toList()
}
