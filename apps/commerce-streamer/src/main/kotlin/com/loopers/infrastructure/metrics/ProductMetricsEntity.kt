package com.loopers.infrastructure.metrics

import com.loopers.domain.BaseEntity
import com.loopers.domain.metrics.ProductMetrics
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_metrics")
class ProductMetricsEntity private constructor(
    productId: Long,
    likeCount: Long,
    salesCount: Long,
    viewCount: Long,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, unique = true, updatable = false)
    var productId: Long = productId
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = likeCount
        protected set

    @Column(name = "sales_count", nullable = false)
    var salesCount: Long = salesCount
        protected set

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = viewCount
        protected set

    fun sync(metrics: ProductMetrics) {
        likeCount = metrics.likeCount
        salesCount = metrics.salesCount
        viewCount = metrics.viewCount
    }

    fun toModel(): ProductMetrics =
        ProductMetrics.of(productId, likeCount, salesCount, viewCount)

    companion object {
        fun from(metrics: ProductMetrics): ProductMetricsEntity = ProductMetricsEntity(
            productId = metrics.productId,
            likeCount = metrics.likeCount,
            salesCount = metrics.salesCount,
            viewCount = metrics.viewCount,
        )
    }
}
