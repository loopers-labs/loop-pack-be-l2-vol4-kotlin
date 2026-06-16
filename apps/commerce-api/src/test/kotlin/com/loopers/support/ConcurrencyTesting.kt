package com.loopers.support

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun runConcurrently(threadCount: Int, task: (index: Int) -> Unit): List<Throwable> {
    val executor = Executors.newFixedThreadPool(threadCount)
    val ready = CountDownLatch(threadCount)
    val start = CountDownLatch(1)
    val done = CountDownLatch(threadCount)
    val failures = ConcurrentLinkedQueue<Throwable>()
    repeat(threadCount) { index ->
        executor.submit {
            ready.countDown()
            start.await()
            try {
                task(index)
            } catch (e: Throwable) {
                failures.add(e)
            } finally {
                done.countDown()
            }
        }
    }
    ready.await()
    start.countDown()
    check(done.await(10, TimeUnit.SECONDS)) { "동시 실행이 10초 안에 끝나지 않았습니다." }
    executor.shutdownNow()
    return failures.toList()
}

fun awaitUntil(timeoutMillis: Long = 5_000, intervalMillis: Long = 50, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "조건이 ${timeoutMillis}ms 안에 충족되지 않았습니다." }
        Thread.sleep(intervalMillis)
    }
}
