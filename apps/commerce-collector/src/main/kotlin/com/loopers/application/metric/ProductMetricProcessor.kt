package com.loopers.application.metric

import com.loopers.infrastructure.metric.EventHandledEntity
import com.loopers.infrastructure.metric.EventHandledJpaRepository
import com.loopers.infrastructure.metric.ProductMetricJpaRepository
import com.loopers.interfaces.consumer.ProductMetricPayload
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId

@Component
class ProductMetricProcessor(
    private val productMetricJpaRepository: ProductMetricJpaRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) {
    @Transactional
    fun process(payload: ProductMetricPayload) {
        if (eventHandledJpaRepository.existsById(payload.eventId)) return

        productMetricJpaRepository.upsert(
            productId = payload.productId,
            type = payload.type,
            metricDate = payload.occurredAt.withZoneSameInstant(ZONE).toLocalDate(),
            delta = payload.delta,
            occurredAt = payload.occurredAt,
        )
        eventHandledJpaRepository.save(EventHandledEntity(eventId = payload.eventId))
    }

    companion object {
        /** "하루치"의 경계는 서비스 기준 시간대로 자른다 — 발생 시각이 어떤 존으로 오든 KST 날짜로 귀속. */
        private val ZONE = ZoneId.of("Asia/Seoul")
    }
}
