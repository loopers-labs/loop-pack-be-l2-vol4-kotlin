package com.loopers.domain.metrics

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 상품 지표 집계 엔티티.
 * Kafka Consumer가 이벤트를 소비하며 upsert 방식으로 갱신한다.
 * 조회수, 좋아요 수, 주문 수, 판매 금액을 실시간에 가깝게 반영한다.
 */
@Entity
@Table(name = "product_metrics")
class ProductMetricsModel(
    productId: Long,
) : BaseEntity() {

    /** 대상 상품 ID (unique) */
    @Column(name = "product_id", nullable = false, unique = true)
    var productId: Long = productId
        protected set

    /** 상품 상세 페이지 조회 수 */
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0
        protected set

    /** 좋아요 수 */
    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0
        protected set

    /** 주문 수량 합계 */
    @Column(name = "order_count", nullable = false)
    var orderCount: Long = 0
        protected set

    /** 판매 금액 합계 */
    @Column(name = "sales_amount", nullable = false)
    var salesAmount: Long = 0
        protected set

    /** 조회수 1 증가 */
    fun incrementView() {
        viewCount++
    }

    /** 좋아요 수 1 증가 */
    fun incrementLike() {
        likeCount++
    }

    /** 좋아요 수 1 감소 (0 미만 방지) */
    fun decrementLike() {
        if (likeCount > 0) likeCount--
    }

    /** 주문 수량 및 판매 금액 가산 */
    fun addOrder(quantity: Long, amount: Long) {
        orderCount += quantity
        salesAmount += amount
    }
}
