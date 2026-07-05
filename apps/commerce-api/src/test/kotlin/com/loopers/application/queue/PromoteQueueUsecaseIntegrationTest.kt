package com.loopers.application.queue

import com.loopers.application.queue.usecase.PromoteQueueUsecase
import com.loopers.domain.queue.OrderQueueRepository
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["queue.capacity=5", "queue.rate-batch=3", "queue.token-ttl-seconds=300"])
@SpringBootTest
class PromoteQueueUsecaseIntegrationTest {
    @Autowired lateinit var promote: PromoteQueueUsecase

    @Autowired lateinit var repository: OrderQueueRepository

    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    @DisplayName("한 tick은 min(rateBatch, capacity-active)명만 발급하고 나머지는 대기 유지한다.")
    @Test
    fun admitsRateBatch() {
        (1L..10L).forEach { repository.enter(it, 1000 + it) }
        val issued = promote.promoteOnce(nowMillis = 2000) // min(3, 5-0)=3
        assertThat(issued).isEqualTo(3)
        assertThat(repository.total()).isEqualTo(7L) // 10-3 대기 유지
        assertThat(repository.findToken(1L)).isNotNull()
    }

    @DisplayName("capacity가 차면 발급하지 않고, active가 줄면(소비) 다음 tick에 다시 발급한다.")
    @Test
    fun capacityBound() {
        (1L..10L).forEach { repository.enter(it, 1000 + it) }
        promote.promoteOnce(2000) // 3 발급 (active=3)
        promote.promoteOnce(2100) // min(3, 5-3)=2 발급 (active=5)
        assertThat(promote.promoteOnce(2200)).isEqualTo(0) // capacity full → 0
        repository.consume(1L) // active=4
        assertThat(promote.promoteOnce(2300)).isEqualTo(1) // min(3, 5-4)=1
    }
}
