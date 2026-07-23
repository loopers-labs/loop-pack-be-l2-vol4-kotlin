package com.loopers.domain.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

/**
 * 상품 랭킹 Materialized View 엔티티.
 * 배치가 집계한 주간/월간 TOP 100 랭킹을 저장한다.
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

    /** 기간 유형 (WEEKLY / MONTHLY) */
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 10)
    var periodType: PeriodType = periodType
        protected set

    /** 기간 키 (WEEKLY: "2026-W30", MONTHLY: "2026-07") */
    @Column(name = "period_key", nullable = false, length = 10)
    var periodKey: String = periodKey
        protected set

    /** 순위 (1-based) */
    @Column(name = "ranking", nullable = false)
    var ranking: Int = ranking
        protected set

    /** 상품 ID */
    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    /** 집계 점수 */
    @Column(name = "score", nullable = false)
    var score: Double = score
        protected set

    /** 집계 기간 내 조회수 합계 */
    @Column(name = "view_count", nullable = false)
    var viewCount: Long = viewCount
        protected set

    /** 집계 기간 내 좋아요 수 합계 */
    @Column(name = "like_count", nullable = false)
    var likeCount: Long = likeCount
        protected set

    /** 집계 기간 내 주문 수 합계 */
    @Column(name = "order_count", nullable = false)
    var orderCount: Long = orderCount
        protected set

    /** 집계 기간 내 판매 금액 합계 */
    @Column(name = "sales_amount", nullable = false)
    var salesAmount: Long = salesAmount
        protected set
}

/** 랭킹 집계 기간 유형 */
enum class PeriodType {
    WEEKLY,
    MONTHLY,
}
