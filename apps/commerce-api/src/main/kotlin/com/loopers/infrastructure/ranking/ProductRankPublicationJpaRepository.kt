package com.loopers.infrastructure.ranking

import com.loopers.infrastructure.ranking.entity.ProductRankPublicationEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ProductRankPublicationJpaRepository : JpaRepository<ProductRankPublicationEntity, Long> {
    fun findFirstByPeriodAndBaseDateLessThanEqualOrderByBaseDateDesc(
        period: String,
        baseDate: LocalDate,
    ): ProductRankPublicationEntity?
}
