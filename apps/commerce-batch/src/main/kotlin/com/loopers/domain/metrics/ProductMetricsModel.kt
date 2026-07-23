package com.loopers.domain.metrics

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 상품 지표 집계 엔티티 (batch 모듈용 읽기 전용 매핑).
 */
@Entity
@Table(name = "product_metrics")
class ProductMetricsModel(
    productId: Long,
) : BaseEntity() {

    @Column(name = "product_id", nullable = false, unique = true)
    var productId: Long = productId
        protected set

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0
        protected set

    @Column(name = "order_count", nullable = false)
    var orderCount: Long = 0
        protected set

    @Column(name = "sales_amount", nullable = false)
    var salesAmount: Long = 0
        protected set
}
