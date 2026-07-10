package com.loopers.metrics.domain

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_metrics")
class ProductMetrics(
    productId: Long,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, unique = true, updatable = false)
    val productId: Long = productId

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0
        private set

    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = 0
        private set

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0
        private set
}
