package com.loopers.queue.application

import com.loopers.queue.domain.QueueErrorCode
import com.loopers.queue.infrastructure.redis.OrderQueueRepository
import com.loopers.support.error.ServiceUnavailableException
import com.loopers.utils.RedisCleanUp
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class OrderQueueFailCloseIntegrationTest @Autowired constructor(
    private val orderQueueService: OrderQueueService,
    private val orderQueueRepository: OrderQueueRepository,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val redisCleanUp: RedisCleanUp,
) {
    @BeforeEach
    fun setUp() {
        redisCleanUp.truncateAll()
        circuitBreakerRegistry.circuitBreaker("order-queue").transitionToOpenState()
    }

    @AfterEach
    fun tearDown() {
        circuitBreakerRegistry.circuitBreaker("order-queue").reset()
    }

    @DisplayName("서킷이 OPEN이면 대기열 진입은 SERVICE_UNAVAILABLE(QUEUE_UNAVAILABLE)로 차단된다.")
    @Test
    fun rejectsEntry_whenCircuitIsOpen() {
        val result = assertThrows<ServiceUnavailableException> { orderQueueService.enter(userId = 1L) }

        assertThat(result.errorCode).isEqualTo(QueueErrorCode.QUEUE_UNAVAILABLE)
    }

    @DisplayName("서킷이 OPEN이면 순번 조회도 SERVICE_UNAVAILABLE(QUEUE_UNAVAILABLE)로 차단된다.")
    @Test
    fun rejectsPositionQuery_whenCircuitIsOpen() {
        val result = assertThrows<ServiceUnavailableException> { orderQueueService.position(userId = 1L) }

        assertThat(result.errorCode).isEqualTo(QueueErrorCode.QUEUE_UNAVAILABLE)
    }

    @DisplayName("서킷이 OPEN이면 주문 검증도 SERVICE_UNAVAILABLE(QUEUE_UNAVAILABLE)로 차단된다. (fail-close)")
    @Test
    fun rejectsAdmissionVerification_whenCircuitIsOpen() {
        val result = assertThrows<ServiceUnavailableException> { orderQueueService.verifyAdmission(userId = 1L) }

        assertThat(result.errorCode).isEqualTo(QueueErrorCode.QUEUE_UNAVAILABLE)
    }

    @DisplayName("서킷이 OPEN이어도 입장 스케줄러는 예외 없이 해당 주기를 건너뛴다.")
    @Test
    fun skipsSchedulerTick_whenCircuitIsOpen() {
        val scheduler = OrderQueueAdmissionScheduler(orderQueueRepository, OrderQueueProperties())

        assertThatCode { scheduler.admit() }.doesNotThrowAnyException()
    }
}
