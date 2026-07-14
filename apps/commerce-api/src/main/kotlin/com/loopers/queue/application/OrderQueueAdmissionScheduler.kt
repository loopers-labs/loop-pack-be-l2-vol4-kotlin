package com.loopers.queue.application

import com.loopers.queue.infrastructure.redis.OrderQueueRepository
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty("queue.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class OrderQueueAdmissionScheduler(
    private val orderQueueRepository: OrderQueueRepository,
    private val properties: OrderQueueProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${queue.scheduler.fixed-delay:100}")
    fun admit() {
        try {
            val tokens = List(properties.scheduler.batchSize) { UUID.randomUUID().toString() }
            val admitted =
                orderQueueRepository.admitNextBatch(properties.scheduler.batchSize, properties.token.ttlSeconds, tokens)
            if (admitted.isNotEmpty()) {
                logger.info("대기열 입장 {}명 (토큰 TTL {}s)", admitted.size, properties.token.ttlSeconds)
            }
        } catch (e: CallNotPermittedException) {
            logger.debug("서킷 OPEN — 이번 드레인 주기를 건너뜁니다.")
        } catch (e: DataAccessException) {
            logger.warn("대기열 드레인 실패 — 다음 주기에 재시도. cause={}", e.javaClass.simpleName)
        }
    }
}
