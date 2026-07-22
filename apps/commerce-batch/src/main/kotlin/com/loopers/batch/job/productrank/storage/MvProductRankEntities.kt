package com.loopers.batch.job.productrank.storage

import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 주간/월간 랭킹 MV. aggregated_date(집계 기간 시작일)별로 TOP 100 스냅샷이 쌓인다.
 * 두 테이블은 구조가 같고, commerce-api가 같은 테이블의 조회 전용 엔티티를 가진다.
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
