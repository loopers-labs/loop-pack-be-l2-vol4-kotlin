package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.ranking.dto.AdminRankingV1Dto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Admin Ranking V1 API", description = "관리자 랭킹 정책 API 입니다.")
interface AdminRankingV1ApiSpec {
    @Operation(
        summary = "랭킹 가중치 변경",
        description = "활성 가중치를 저장하고 오늘의 전체 랭킹을 즉시 재계산합니다.",
    )
    fun updateWeights(
        adminId: String,
        request: AdminRankingV1Dto.UpdateWeightsRequest,
    ): ApiResponse<AdminRankingV1Dto.RankingWeightsResponse>
}
