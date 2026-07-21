package com.loopers.batch.metrics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault
import java.io.Serializable
import java.time.ZonedDateTime

data class ProductMetricsWeeklyId(
    val productId: Long = 0,
    val yearWeek: String = "",
) : Serializable

@Entity
@Table(name = "product_metrics_weekly")
@IdClass(ProductMetricsWeeklyId::class)
class ProductMetricsWeekly(
    @Id
    @Column("product_id")
    val productId: Long,

    @Id
    @Column(name = "year_week")
    val yearWeek: String,

    @ColumnDefault("0")
    val likeCount: Int,

    @ColumnDefault("0")
    val salesCount: Int,

    @ColumnDefault("0")
    val viewCount: Int,

    @ColumnDefault("0")
    val score: Double,

    @Column(name = "updated_at")
    val updatedAt: ZonedDateTime,
)
