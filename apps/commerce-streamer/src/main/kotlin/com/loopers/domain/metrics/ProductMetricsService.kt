package com.loopers.domain.metrics

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 상품 지표 갱신 서비스.
 * Consumer가 Kafka 이벤트를 처리하며 이 서비스를 통해 product_metrics를 upsert한다.
 */
@Component
class ProductMetricsService(
    private val productMetricsRepository: ProductMetricsRepository,
) {

    /** 조회수 1 증가 */
    @Transactional
    fun incrementView(productId: Long) {
        val metrics = getOrCreate(productId)
        metrics.incrementView()
        productMetricsRepository.save(metrics)
    }

    /** 좋아요 수 1 증가 */
    @Transactional
    fun incrementLike(productId: Long) {
        val metrics = getOrCreate(productId)
        metrics.incrementLike()
        productMetricsRepository.save(metrics)
    }

    /** 좋아요 수 1 감소 */
    @Transactional
    fun decrementLike(productId: Long) {
        val metrics = getOrCreate(productId)
        metrics.decrementLike()
        productMetricsRepository.save(metrics)
    }

    /** 주문 수량 및 금액 가산 */
    @Transactional
    fun addOrder(productId: Long, quantity: Long, amount: Long) {
        val metrics = getOrCreate(productId)
        metrics.addOrder(quantity, amount)
        productMetricsRepository.save(metrics)
    }

    /** 없으면 새로 생성하여 반환 (upsert의 create 부분) */
    private fun getOrCreate(productId: Long): ProductMetricsModel {
        return productMetricsRepository.findByProductId(productId)
            ?: productMetricsRepository.save(ProductMetricsModel(productId))
    }
}
