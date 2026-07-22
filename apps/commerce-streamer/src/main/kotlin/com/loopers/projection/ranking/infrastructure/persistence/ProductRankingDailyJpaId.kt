package com.loopers.projection.ranking.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable
import java.time.LocalDate

@Embeddable
data class ProductRankingDailyJpaId(
    @Column(name = "ranking_date", nullable = false)
    var rankingDate: LocalDate = LocalDate.EPOCH,
    @Column(name = "product_id", nullable = false)
    var productId: Long = 0L,
) : Serializable
