package com.loopers.ranking.domain

import java.math.BigDecimal

object RankingWeights {
    val VIEW: BigDecimal = BigDecimal("0.1")
    val LIKE: BigDecimal = BigDecimal("0.2")
    val ORDER_LINE: BigDecimal = BigDecimal("0.7")
    val CARRY_RATE: BigDecimal = BigDecimal("0.1")
}
