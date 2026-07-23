package com.loopers.batch.metrics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault

@Entity
@Table(name = "product_metrics_snapshot")
class ProductMetricsSnapshot(
    @Id
    @Column(name = "product_id")
    val productId: Long,
    @ColumnDefault("0")
    val likeCount: Int,
    @ColumnDefault("0")
    val salesCount: Int,
    @ColumnDefault("0")
    val viewCount: Int,
)