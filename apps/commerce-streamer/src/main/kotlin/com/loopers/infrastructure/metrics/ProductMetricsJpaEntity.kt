package com.loopers.infrastructure.metrics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "product_metrics")
class ProductMetricsJpaEntity(
    @Id
    @Column(name = "product_id")
    val productId: Long,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,

    @Column(name = "order_count", nullable = false)
    var orderCount: Long = 0,

    @Column(name = "sales_amount", nullable = false)
    var salesAmount: Long = 0,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,
) {
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: ZonedDateTime
        protected set

    @PrePersist
    private fun prePersist() {
        val now = ZonedDateTime.now()
        createdAt = now
        updatedAt = now
    }

    @PreUpdate
    private fun preUpdate() {
        updatedAt = ZonedDateTime.now()
    }
}
