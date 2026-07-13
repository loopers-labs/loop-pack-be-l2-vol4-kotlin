package com.loopers.interfaces.api.ranking.dto

import com.loopers.application.ranking.dto.RankingWeightCommand
import com.loopers.domain.ranking.RankingWeights

class AdminRankingV1Dto {
    data class UpdateWeightsRequest(
        val viewWeight: Double,
        val likeWeight: Double,
        val salesWeight: Double,
    ) {
        fun toCommand(): RankingWeightCommand {
            return RankingWeightCommand(
                viewWeight = viewWeight,
                likeWeight = likeWeight,
                salesWeight = salesWeight,
            )
        }
    }

    data class RankingWeightsResponse(
        val viewWeight: Double,
        val likeWeight: Double,
        val salesWeight: Double,
    ) {
        companion object {
            fun from(weights: RankingWeights): RankingWeightsResponse {
                return RankingWeightsResponse(
                    viewWeight = weights.view,
                    likeWeight = weights.like,
                    salesWeight = weights.sales,
                )
            }
        }
    }
}
