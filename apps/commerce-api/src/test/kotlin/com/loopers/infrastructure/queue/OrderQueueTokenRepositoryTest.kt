package com.loopers.infrastructure.queue

import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class OrderQueueTokenRepositoryTest {
    @Autowired lateinit var repository: OrderQueueRepository

    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("토큰 발급 후 조회되고, active(processing)에 집계된다. claim이 성공하면 토큰만 사라지고 release로 processing이 회수된다.")
    @Test
    fun issueClaimRelease() {
        repository.issueToken(userId = 1L, token = "tok-1", ttlSeconds = 300, nowMillis = 1000)
        assertThat(repository.findToken(1L)).isEqualTo("tok-1")
        assertThat(repository.countActive()).isEqualTo(1L)

        assertThat(repository.claimToken(1L, "tok-1")).isTrue()
        assertThat(repository.findToken(1L)).isNull() // claim = 검증+소비 원자화
        assertThat(repository.countActive()).isEqualTo(1L) // 주문 처리 중 — processing 유지

        repository.release(1L)
        assertThat(repository.countActive()).isEqualTo(0L)
    }

    @DisplayName("claim은 토큰이 일치할 때만 소비하고, 불일치하면 기존 토큰을 보존한다(compare-and-delete).")
    @Test
    fun claimKeepsTokenOnMismatch() {
        repository.issueToken(userId = 1L, token = "tok-1", ttlSeconds = 300, nowMillis = 1000)

        assertThat(repository.claimToken(1L, "wrong")).isFalse()
        assertThat(repository.findToken(1L)).isEqualTo("tok-1") // 잘못된 요청이 유효 토큰을 파괴하지 않음

        assertThat(repository.claimToken(1L, "tok-1")).isTrue()
        assertThat(repository.claimToken(1L, "tok-1")).isFalse() // 재사용 불가
    }

    @DisplayName("popNext는 앞에서 N명을 원자적으로 꺼낸다.")
    @Test
    fun popNext() {
        repository.enter(1L, 1000)
        repository.enter(2L, 1001)
        repository.enter(3L, 1002)
        assertThat(repository.popNext(2)).containsExactly(1L, 2L)
        assertThat(repository.total()).isEqualTo(1L)
    }

    @DisplayName("pruneExpiredProcessing은 임계 이전 발급분만 회수하고, 토큰 키는 TTL이 소멸을 담당한다.")
    @Test
    fun prune() {
        repository.issueToken(1L, "a", 300, nowMillis = 1000) // score=1000
        repository.issueToken(2L, "b", 300, nowMillis = 9000) // score=9000
        repository.pruneExpiredProcessing(beforeMillis = 5000) // 1000 < 5000 → 회수
        assertThat(repository.countActive()).isEqualTo(1L)
        assertThat(repository.findToken(1L)).isEqualTo("a") // 프룬은 processing만 회수 — 토큰 키는 TTL 만료로 소멸
    }
}
