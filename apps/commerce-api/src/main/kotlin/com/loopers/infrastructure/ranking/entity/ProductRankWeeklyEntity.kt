package com.loopers.infrastructure.ranking.entity

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(
    name = "mv_product_rank_weekly",
    indexes = [
        Index(name = "uk_mv_product_rank_weekly_base_product", columnList = "base_date, product_id", unique = true),
        Index(name = "idx_mv_product_rank_weekly_top", columnList = "base_date, ranking_score DESC, product_id ASC"),
    ],
)
class ProductRankWeeklyEntity(
    @Column(name = "base_date", nullable = false)
    var baseDate: LocalDate,

    @Column(name = "product_id", nullable = false)
    var productId: Long,

    @Column(name = "ranking_score", nullable = false)
    var rankingScore: Double,
) : BaseEntity()
