package com.loopers.domain.ranking

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "product_ranking_baseline")
class ProductRankingBaseline(
    @Id
    @Column(name = "product_id")
    val productId: Long,
    val baselineDate: LocalDate,
    val viewCount: Int,
    val likeCount: Int,
    val salesCount: Int,
)
