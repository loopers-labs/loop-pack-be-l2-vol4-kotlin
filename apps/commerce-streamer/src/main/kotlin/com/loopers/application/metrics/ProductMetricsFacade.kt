package com.loopers.application.metrics

import com.loopers.domain.metrics.ProcessedEventRepository
import com.loopers.domain.metrics.ProductMetrics
import com.loopers.domain.metrics.ProductMetricsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 상품 지표 집계의 트랜잭션 경계·조율을 단일 소유한다.
 * 각 연산은 멱등 표식(event_handled) 기록과 지표 upsert 를 **같은 트랜잭션**에서 처리한다 —
 * 처리 후 ack 전에 크래시가 나 같은 메시지가 재전달돼도, 멱등 표식이 남아 있어 결과는 1회만 반영된다.
 *
 * 이벤트 타입은 리스너가 도메인 의도로 번역해 넘긴다 — 이 컴포넌트는 eventType 문자열을 모른다.
 */
@Component
class ProductMetricsFacade(
    private val productMetricsRepository: ProductMetricsRepository,
    private val processedEventRepository: ProcessedEventRepository,
) {
    @Transactional
    fun increaseLike(eventId: UUID, productId: Long) = onlyOnce(eventId) {
        apply(productId) { it.increaseLike() }
    }

    @Transactional
    fun decreaseLike(eventId: UUID, productId: Long) = onlyOnce(eventId) {
        apply(productId) { it.decreaseLike() }
    }

    @Transactional
    fun increaseView(eventId: UUID, productId: Long) = onlyOnce(eventId) {
        apply(productId) { it.increaseView() }
    }

    @Transactional
    fun addSales(eventId: UUID, lines: List<SalesLine>) = onlyOnce(eventId) {
        lines.forEach { line -> apply(line.productId) { it.addSales(line.quantity) } }
    }

    private fun onlyOnce(eventId: UUID, block: () -> Unit) {
        if (processedEventRepository.existsByEventId(eventId)) return
        block()
        processedEventRepository.save(eventId)
    }

    private fun apply(productId: Long, change: (ProductMetrics) -> Unit) {
        val metrics = productMetricsRepository.findByProductId(productId) ?: ProductMetrics.create(productId)
        change(metrics)
        productMetricsRepository.save(metrics)
    }
}
