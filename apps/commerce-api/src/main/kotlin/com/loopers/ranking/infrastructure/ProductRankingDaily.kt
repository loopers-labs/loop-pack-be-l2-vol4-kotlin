package com.loopers.ranking.infrastructure

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    name = "product_ranking_daily",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_prd_date_product", columnNames = ["ranking_date", "product_id"]),
    ],
    indexes = [
        Index(name = "idx_prd_date_score", columnList = "ranking_date, score DESC, product_id ASC"),
    ],
)
class ProductRankingDaily(
    rankingDate: LocalDate,
    productId: Long,
    score: BigDecimal,
) : BaseEntity() {
    @Column(name = "ranking_date", nullable = false, updatable = false)
    val rankingDate: LocalDate = rankingDate

    @Column(name = "product_id", nullable = false, updatable = false)
    val productId: Long = productId

    @Column(name = "score", nullable = false, precision = 20, scale = 4)
    var score: BigDecimal = score
        private set
}
