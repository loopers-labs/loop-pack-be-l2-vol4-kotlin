package com.loopers.infrastructure.ranking

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "mv_product_rank_monthly")
@IdClass(MvProductRankId::class)
class MvProductRankMonthlyJpaEntity(
    @Id
    @Column(name = "product_id")
    val productId: Long,
    @Id
    @Column(name = "period_start")
    val periodStart: LocalDate,
    @Column(name = "ranking_score", nullable = false)
    var rankingScore: Double = 0.0,
    @Column(name = "`rank`", nullable = false)
    var rank: Int = 0,
)
