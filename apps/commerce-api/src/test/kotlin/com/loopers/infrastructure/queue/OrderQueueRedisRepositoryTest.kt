package com.loopers.infrastructure.queue

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.support.runConcurrently
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest
class OrderQueueRedisRepositoryTest {
    @Autowired lateinit var repository: OrderQueueRepository

    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("진입 순서대로 순번이 부여되고, 전체 대기 인원이 집계된다.")
    @Test
    fun entersInOrder() {
        assertThat(repository.enter(userId = 1L, nowMillis = 1000)).isEqualTo(1L)
        assertThat(repository.enter(userId = 2L, nowMillis = 1001)).isEqualTo(2L)
        assertThat(repository.rank(1L)).isEqualTo(0L)
        assertThat(repository.rank(2L)).isEqualTo(1L)
        assertThat(repository.total()).isEqualTo(2L)
    }

    @DisplayName("같은 userId가 재진입해도 순번과 인원은 유지된다(중복 방지).")
    @Test
    fun idempotentReentry() {
        repository.enter(userId = 1L, nowMillis = 1000)
        val second = repository.enter(userId = 1L, nowMillis = 5000)
        assertThat(second).isEqualTo(1L)
        assertThat(repository.total()).isEqualTo(1L)
    }

    @DisplayName("미대기 유저의 rank는 null이다.")
    @Test
    fun rankNullWhenNotWaiting() {
        assertThat(repository.rank(99L)).isNull()
    }

    @DisplayName("동시에 N명이 진입해도 전체 인원은 정확히 N, 순번은 0..N-1로 유일하다.")
    @Test
    fun concurrentEnter() {
        val n = 200
        val seq = AtomicLong(1_000_000)
        runConcurrently(threadCount = n) { i ->
            repository.enter(userId = (i + 1).toLong(), nowMillis = seq.incrementAndGet())
        }
        assertThat(repository.total()).isEqualTo(n.toLong())
        val ranks = (0 until n).map { repository.rank((it + 1).toLong()) }
        assertThat(ranks.filterNotNull().toSet()).hasSize(n) // 모든 rank 유일
    }
}
