package com.loopers.domain.productmetric

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(
    name = "product_metric_weekly",
    indexes = [
        Index(name = "uk_product_metric_weekly_base_product", columnList = "base_date, product_id", unique = true),
    ],
)
class ProductMetricWeekly(
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
) : BaseEntity()
