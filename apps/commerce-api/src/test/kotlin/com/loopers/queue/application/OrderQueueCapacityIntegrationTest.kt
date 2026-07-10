package com.loopers.queue.application

import com.loopers.queue.domain.QueueErrorCode
import com.loopers.queue.infrastructure.redis.OrderQueueRepository
import com.loopers.support.error.ConflictException
import com.loopers.support.error.TooManyRequestsException
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(properties = ["queue.max-waiting=1"])
@ActiveProfiles("test")
class OrderQueueCapacityIntegrationTest @Autowired constructor(
    private val orderQueueService: OrderQueueService,
    private val orderQueueRepository: OrderQueueRepository,
    private val redisCleanUp: RedisCleanUp,
) {
    @BeforeEach
    fun setUp() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("대기 인원이 상한에 도달하면 새 진입은 TOO_MANY_REQUESTS(QUEUE_FULL)로 거절된다.")
    @Test
    fun rejectsEntry_whenQueueIsFull() {
        orderQueueService.enter(userId = 1L)

        val result = assertThrows<TooManyRequestsException> { orderQueueService.enter(userId = 2L) }

        assertThat(result.errorCode).isEqualTo(QueueErrorCode.QUEUE_FULL)
    }

    @DisplayName("가득 찬 큐에서 한 명이 입장하면 다음 진입이 다시 허용된다.")
    @Test
    fun allowsEntry_afterDrainFreesCapacity() {
        orderQueueService.enter(userId = 1L)
        assertThrows<TooManyRequestsException> { orderQueueService.enter(userId = 2L) }

        orderQueueRepository.admitNextBatch(batchSize = 1, tokenTtlSeconds = 300, tokens = listOf("tk-1"))
        val info = orderQueueService.enter(userId = 2L)

        assertAll(
            { assertThat(info.status).isEqualTo(QueueEntryStatus.WAITING) },
            { assertThat(info.position).isEqualTo(1L) },
        )
    }

    @DisplayName("큐가 가득 차도 입장 토큰 보유자의 재진입은 QUEUE_FULL이 아니라 ALREADY_ADMITTED로 거절된다.")
    @Test
    fun distinguishesReEntry_fromFullQueue() {
        orderQueueService.enter(userId = 1L)
        orderQueueRepository.admitNextBatch(batchSize = 1, tokenTtlSeconds = 300, tokens = listOf("tk-1"))
        orderQueueService.enter(userId = 2L)

        val result = assertThrows<ConflictException> { orderQueueService.enter(userId = 1L) }

        assertThat(result.errorCode).isEqualTo(QueueErrorCode.ALREADY_ADMITTED)
    }
}
