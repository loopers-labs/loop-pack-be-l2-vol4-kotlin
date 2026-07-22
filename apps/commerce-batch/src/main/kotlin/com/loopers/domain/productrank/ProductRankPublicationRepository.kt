package com.loopers.domain.productrank

import java.time.LocalDate

interface ProductRankPublicationRepository {
    fun publish(
        period: String,
        baseDate: LocalDate,
        generationId: String,
    )
}
