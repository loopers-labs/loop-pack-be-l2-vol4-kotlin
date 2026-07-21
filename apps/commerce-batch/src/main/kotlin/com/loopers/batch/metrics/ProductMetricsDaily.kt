package com.loopers.batch.metrics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault
import java.io.Serializable
import java.time.LocalDate

@Entity
@Table(name = "product_metrics_daily")
@IdClass(ProductMetricsDailyId::class)
class ProductMetricsDaily(
    @Id
    @Column(name = "product_id")
    val productId: Long,

    @Id
    @Column(name = "metric_date")
    val metricDate: LocalDate,

    @ColumnDefault("0")
    val likeCount: Int,

    @ColumnDefault("0")
    val salesCount: Int,

    @ColumnDefault("0")
    val viewCount: Int,
)

data class ProductMetricsDailyId(
    val productId: Long = 0,
    val metricDate: LocalDate = LocalDate.EPOCH,
) : Serializable
