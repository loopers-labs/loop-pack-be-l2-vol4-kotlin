package com.loopers.application.metrics

import com.loopers.domain.metrics.EventHandledRepository
import com.loopers.domain.metrics.ProductMetricRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class MetricDelta(
    val productId: Long,
    val like: Long = 0,
    val sales: Long = 0,
    val view: Long = 0,
)

@Service
class ProductMetricsService(
    private val eventHandledRepository: EventHandledRepository,
    private val productMetricRepository: ProductMetricRepository,
) {
    // eventId 신규일 때만 delta 적용 — event_handled 기록과 집계가 한 트랜잭션(멱등).
    @Transactional
    fun applyOnce(eventId: String, deltas: List<MetricDelta>) {
        if (!eventHandledRepository.markHandled(eventId)) return
        deltas.forEach {
            productMetricRepository.upsertDelta(it.productId, it.like, it.sales, it.view)
        }
    }
}
