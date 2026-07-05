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

    @DisplayName("토큰 발급 후 조회되고, active(processing)에 집계되며, 소비하면 사라진다.")
    @Test
    fun issueFindConsume() {
        repository.issueToken(userId = 1L, token = "tok-1", ttlSeconds = 300, nowMillis = 1000)
        assertThat(repository.findToken(1L)).isEqualTo("tok-1")
        assertThat(repository.countActive()).isEqualTo(1L)

        repository.consume(1L)
        assertThat(repository.findToken(1L)).isNull()
        assertThat(repository.countActive()).isEqualTo(0L)
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

    @DisplayName("pruneExpiredProcessing은 임계 이전 발급분을 회수한다(만료 자리 반환).")
    @Test
    fun prune() {
        repository.issueToken(1L, "a", 300, nowMillis = 1000) // score=1000
        repository.issueToken(2L, "b", 300, nowMillis = 9000) // score=9000
        repository.pruneExpiredProcessing(beforeMillis = 5000) // 1000 < 5000 → 회수
        assertThat(repository.countActive()).isEqualTo(1L)
    }
}
