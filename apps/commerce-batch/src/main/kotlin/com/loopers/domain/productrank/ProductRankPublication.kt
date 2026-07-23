package com.loopers.domain.productrank

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.ZonedDateTime

@Entity
@Table(
    name = "product_rank_publication",
    indexes = [
        Index(name = "uk_product_rank_publication_period_base", columnList = "period, base_date", unique = true),
        Index(name = "idx_product_rank_publication_lookup", columnList = "period, base_date"),
    ],
)
class ProductRankPublication(
    @Column(nullable = false, length = 20)
    var period: String,

    @Column(name = "base_date", nullable = false)
    var baseDate: LocalDate,

    @Column(name = "generation_id", nullable = false, length = 64)
    var generationId: String,

    @Column(name = "published_at", nullable = false)
    var publishedAt: ZonedDateTime,
) : BaseEntity()
