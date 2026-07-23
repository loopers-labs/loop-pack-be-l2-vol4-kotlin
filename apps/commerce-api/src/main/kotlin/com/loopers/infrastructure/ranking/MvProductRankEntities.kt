package com.loopers.infrastructure.ranking

import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 주간/월간 랭킹 MV의 조회 전용 매핑. 쓰기는 commerce-batch(productRank* Job)가 담당한다 —
 * 스키마 변경 시 batch의 MvProductRankEntities와 동기화할 것.
 */
@Entity
@Table(
    name = "mv_product_rank_weekly",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_mv_product_rank_weekly_date_rank", columnNames = ["aggregated_date", "rank_no"]),
    ],
)
class MvProductRankWeeklyEntity(
    @Column(name = "rank_no", nullable = false)
    val rankNo: Int,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "score", nullable = false)
    val score: Long,

    @Column(name = "aggregated_date", nullable = false)
    val aggregatedDate: LocalDate,
) : BaseEntity()

@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_mv_product_rank_monthly_date_rank", columnNames = ["aggregated_date", "rank_no"]),
    ],
)
class MvProductRankMonthlyEntity(
    @Column(name = "rank_no", nullable = false)
    val rankNo: Int,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "score", nullable = false)
    val score: Long,

    @Column(name = "aggregated_date", nullable = false)
    val aggregatedDate: LocalDate,
) : BaseEntity()
