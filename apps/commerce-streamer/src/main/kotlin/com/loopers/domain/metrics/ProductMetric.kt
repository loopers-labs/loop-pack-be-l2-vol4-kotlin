package com.loopers.domain.metrics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "product_metrics")
class ProductMetric(
    @Id
    @Column(name = "product_id")
    val productId: Long,
    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0,
    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = 0,
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = 0,
)
