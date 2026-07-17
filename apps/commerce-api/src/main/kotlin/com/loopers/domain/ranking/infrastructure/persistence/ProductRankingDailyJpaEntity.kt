package com.loopers.domain.ranking.infrastructure.persistence

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "product_ranking_daily",
    indexes = [
        Index(name = "idx_ranking_date_rank", columnList = "ranking_date, rank_no"),
    ],
)
class ProductRankingDailyJpaEntity(
    @EmbeddedId
    var id: ProductRankingDailyJpaId,
    @Column(name = "rank_no", nullable = false)
    var rankNo: Int,
    @Column(name = "score", nullable = false)
    var score: Double,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
