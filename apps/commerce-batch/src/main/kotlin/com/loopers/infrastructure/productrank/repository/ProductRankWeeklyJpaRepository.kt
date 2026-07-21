package com.loopers.infrastructure.productrank.repository

import com.loopers.infrastructure.productrank.entity.ProductRankWeeklyEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ProductRankWeeklyJpaRepository : JpaRepository<ProductRankWeeklyEntity, Long> {
    @Modifying
    fun deleteByBaseDate(baseDate: LocalDate): Int

    fun findTop100ByBaseDateOrderByRankingScoreDescProductIdAsc(baseDate: LocalDate): List<ProductRankWeeklyEntity>

    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO mv_product_rank_weekly (
                base_date,
                product_id,
                ranking_score,
                created_at,
                updated_at
            ) VALUES (
                :baseDate,
                :productId,
                :rankingScore,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                ranking_score = VALUES(ranking_score),
                updated_at = CURRENT_TIMESTAMP(6)
        """,
    )
    fun upsert(
        @Param("baseDate") baseDate: LocalDate,
        @Param("productId") productId: Long,
        @Param("rankingScore") rankingScore: Double,
    ): Int
}
