package com.loopers.domain.metrics

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnDefault

@Entity
@Table(name = "product_metrics")
class ProductMetricsModel(
    @Id
    @Column(name = "product_id")
    val id: Long,
    @ColumnDefault("0")
    val likeCount: Int,
    @ColumnDefault("0")
    val salesCount: Int,
    @ColumnDefault("0")
    val viewCount: Int,
    @ColumnDefault("0")
    val version: Long,
)
