package com.loopers.infrastructure.productrank.repository

import com.loopers.domain.productrank.ProductRankPublicationRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class ProductRankPublicationRepositoryImpl(
    private val productRankPublicationJpaRepository: ProductRankPublicationJpaRepository,
) : ProductRankPublicationRepository {
    @Transactional
    override fun publish(
        period: String,
        baseDate: LocalDate,
        generationId: String,
    ) {
        productRankPublicationJpaRepository.publish(period, baseDate, generationId)
    }
}
