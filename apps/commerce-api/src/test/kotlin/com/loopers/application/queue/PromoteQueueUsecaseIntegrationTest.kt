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

@SpringBootTest
class PromoteQueueUsecaseIntegrationTest {
    @Autowired lateinit var repository: OrderQueueRepository

    @Autowired lateinit var redisCleanUp: RedisCleanUp

    @AfterEach fun tearDown() = redisCleanUp.truncateAll()

    // 버킷 잔량(in-memory 상태)이 테스트 간 공유되지 않도록 usecase를 테스트마다 직접 생성한다.
    private fun promoteUsecase(capacity: Long = 5, refillPerSecond: Long = 10, burst: Long = 3) =
        PromoteQueueUsecase(repository, capacity, refillPerSecond, burst, 300)

    @DisplayName("시작 버킷은 burst만큼 차 있고, 같은 시각 재호출은 refill 0이라 발급하지 않는다.")
    @Test
    fun startsFullAndDrains() {
        val promote = promoteUsecase()
        (1L..10L).forEach { repository.enter(it, 1000 + it) }
        assertThat(promote.promoteOnce(nowMillis = 2000)).isEqualTo(3) // 초기 버킷 = burst(3)
        assertThat(promote.promoteOnce(nowMillis = 2000)).isEqualTo(0) // 버킷 소진 + 경과 0
        assertThat(repository.total()).isEqualTo(7L) // 10-3 대기 유지
        assertThat(repository.findToken(1L)).isNotNull()
    }

    @DisplayName("경과 시간만큼 토큰이 refill된다(100ms × 10/s = 1명씩).")
    @Test
    fun refillsByElapsedTime() {
        val promote = promoteUsecase(capacity = 50)
        (1L..10L).forEach { repository.enter(it, 1000 + it) }
        promote.promoteOnce(2000) // burst 3 소진
        assertThat(promote.promoteOnce(2100)).isEqualTo(1) // 0.1s × 10/s = 1
        assertThat(promote.promoteOnce(2200)).isEqualTo(1)
    }

    @DisplayName("한산한 구간 동안 토큰은 burst까지만 누적된다(버스트 상한).")
    @Test
    fun accumulationCappedAtBurst() {
        val promote = promoteUsecase(capacity = 50)
        (1L..10L).forEach { repository.enter(it, 1000 + it) }
        promote.promoteOnce(2000) // burst 3 소진
        assertThat(promote.promoteOnce(12000)).isEqualTo(3) // 10초 경과 → refill 100이어도 cap = burst(3)
    }

    @DisplayName("capacity가 차면 버킷에 토큰이 있어도 발급하지 않고, 소비로 active가 줄면 다시 발급한다.")
    @Test
    fun capacityBound() {
        val promote = promoteUsecase() // capacity=5
        (1L..10L).forEach { repository.enter(it, 1000 + it) }
        promote.promoteOnce(2000) // burst 3 발급 (active=3)
        promote.promoteOnce(2100) // refill 1 → min(1, 5-3)=1 (active=4)
        promote.promoteOnce(2200) // refill 1 → min(1, 5-4)=1 (active=5)
        assertThat(promote.promoteOnce(2300)).isEqualTo(0) // refill 1 있어도 capacity full → 0 (버킷 잔량 보존)
        repository.release(1L) // 주문 완료 → active=4
        assertThat(promote.promoteOnce(2400)).isEqualTo(1) // min(버킷 2, free 1) = 1
    }

    @DisplayName("토큰 TTL 만료분은 프룬으로 capacity가 회복되어 다음 유저가 발급받는다.")
    @Test
    fun expiredProcessingRestoresCapacity() {
        val promote = promoteUsecase()
        (1L..10L).forEach { repository.enter(it, 1000 + it) }
        promote.promoteOnce(2000) // burst 3 발급 (active=3, score=2000)
        val afterTtl = 2000 + 300 * 1000 + 500L // TTL(300s) 경과
        assertThat(promote.promoteOnce(afterTtl)).isEqualTo(3) // 프룬으로 active=0 → burst만큼 재발급
        assertThat(repository.countActive()).isEqualTo(3L)
    }
}
