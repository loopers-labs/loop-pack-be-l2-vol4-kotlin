package com.loopers.infrastructure.productmetric.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.productmetric.ProductMetricMonthly
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(
    name = "product_metric_monthly",
    indexes = [
        Index(name = "uk_product_metric_monthly_base_product", columnList = "base_date, product_id", unique = true),
    ],
)
class ProductMetricMonthlyEntity(
    @Column(name = "base_date", nullable = false)
    var baseDate: LocalDate,

    @Column(name = "product_id", nullable = false)
    var productId: Long,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long,

    @Column(name = "sales_amount", nullable = false)
    var salesAmount: Long,
) : BaseEntity() {
    fun toDomain(): ProductMetricMonthly {
        return ProductMetricMonthly(
            id = id,
            baseDate = baseDate,
            productId = productId,
            viewCount = viewCount,
            likeCount = likeCount,
            salesAmount = salesAmount,
        )
    }
}
