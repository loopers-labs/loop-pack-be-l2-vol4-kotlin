package com.loopers.infrastructure.productrank.repository

import com.loopers.domain.productrank.ProductRankPublication
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface ProductRankPublicationJpaRepository : JpaRepository<ProductRankPublication, Long> {
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO product_rank_publication (
                period,
                base_date,
                generation_id,
                published_at,
                created_at,
                updated_at
            ) VALUES (
                :period,
                :baseDate,
                :generationId,
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6),
                CURRENT_TIMESTAMP(6)
            )
            ON DUPLICATE KEY UPDATE
                generation_id = VALUES(generation_id),
                published_at = CURRENT_TIMESTAMP(6),
                updated_at = CURRENT_TIMESTAMP(6)
        """,
    )
    fun publish(
        @Param("period") period: String,
        @Param("baseDate") baseDate: LocalDate,
        @Param("generationId") generationId: String,
    ): Int
}
