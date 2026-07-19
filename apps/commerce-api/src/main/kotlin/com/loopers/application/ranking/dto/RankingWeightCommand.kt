package com.loopers.application.ranking.dto

import com.loopers.domain.ranking.RankingWeights
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

data class RankingWeightCommand(
    val viewWeight: Double,
    val likeWeight: Double,
    val salesWeight: Double,
) {
    init {
        validate("viewWeight", viewWeight)
        validate("likeWeight", likeWeight)
        validate("salesWeight", salesWeight)
    }

    fun toWeights(): RankingWeights {
        return RankingWeights(
            view = viewWeight,
            like = likeWeight,
            sales = salesWeight,
        )
    }

    private fun validate(name: String, value: Double) {
        if (!value.isFinite() || value < 0.0) {
            throw CoreException(ErrorType.BAD_REQUEST, "$name must be a non-negative finite number.")
        }
    }
}
