package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 상품 랭킹 Materialized View 엔티티 (조회 전용).
 */
@Entity
@Table(
    name = "mv_product_rank",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["period_type", "period_key", "ranking"]),
    ],
)
class ProductRankModel(
    periodType: PeriodType,
    periodKey: String,
    ranking: Int,
    productId: Long,
    score: Double,
    viewCount: Long,
    likeCount: Long,
    orderCount: Long,
    salesAmount: Long,
) : BaseEntity() {

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    var periodType: PeriodType = periodType
        protected set

    @Column(name = "period_key", nullable = false, length = 10)
    var periodKey: String = periodKey
        protected set

    @Column(name = "ranking", nullable = false)
    var ranking: Int = ranking
        protected set

    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    @Column(name = "score", nullable = false)
    var score: Double = score
        protected set

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = viewCount
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = likeCount
        protected set

    @Column(name = "order_count", nullable = false)
    var orderCount: Long = orderCount
        protected set

    @Column(name = "sales_amount", nullable = false)
    var salesAmount: Long = salesAmount
        protected set
}

/** 랭킹 집계 기간 유형 */
enum class PeriodType {
    WEEKLY,
    MONTHLY,
}
