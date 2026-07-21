package com.loopers.infrastructure.productmetric.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.productmetric.ProductMetricDaily
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(
    name = "product_metric_daily",
    indexes = [
        Index(name = "uk_product_metric_daily_date_product", columnList = "metric_date, product_id", unique = true),
        Index(name = "idx_product_metric_daily_product_date", columnList = "product_id, metric_date"),
    ],
)
class ProductMetricDailyEntity(
    @Column(name = "metric_date", nullable = false)
    var metricDate: LocalDate,

    @Column(name = "product_id", nullable = false)
    var productId: Long,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long,

    @Column(name = "sales_amount", nullable = false)
    var salesAmount: Long,
) : BaseEntity() {
    fun toDomain(): ProductMetricDaily {
        return ProductMetricDaily(
            id = id,
            metricDate = metricDate,
            productId = productId,
            viewCount = viewCount,
            likeCount = likeCount,
            salesAmount = salesAmount,
        )
    }
}
