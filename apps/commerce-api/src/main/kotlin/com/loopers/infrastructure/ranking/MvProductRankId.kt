package com.loopers.infrastructure.ranking

import java.io.Serializable
import java.time.LocalDate

data class MvProductRankId(
    val productId: Long = 0,
    val periodStart: LocalDate = LocalDate.MIN,
) : Serializable
