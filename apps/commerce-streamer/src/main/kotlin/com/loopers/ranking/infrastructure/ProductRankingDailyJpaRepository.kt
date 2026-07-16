package com.loopers.ranking.infrastructure

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.math.BigDecimal
import java.time.LocalDate

interface ProductRankingDailyJpaRepository : JpaRepository<ProductRankingDaily, Long> {
    @Modifying
    @Query(
        value = """
            insert into product_ranking_daily (ranking_date, product_id, score, created_at, updated_at)
            values (:rankingDate, :productId, :change, now(), now())
            on duplicate key update
                score = score + :change,
                updated_at = now()
        """,
        nativeQuery = true,
    )
    fun upsertChange(rankingDate: LocalDate, productId: Long, change: BigDecimal): Int
}
