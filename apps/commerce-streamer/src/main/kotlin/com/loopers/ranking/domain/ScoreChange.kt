package com.loopers.ranking.domain

import java.math.BigDecimal

data class ScoreChange(
    val productId: Long,
    val amount: BigDecimal,
)
