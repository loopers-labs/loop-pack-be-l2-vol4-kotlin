package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.ranking.dto.RankingV1Dto
import com.loopers.domain.ranking.RankingPeriod
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Ranking V1 API", description = "일별 상품 랭킹 조회 API 입니다.")
interface RankingV1ApiSpec {
    @Operation(
        summary = "일별 상품 랭킹 조회",
        description = "yyyyMMdd 날짜와 0-based page로 상품 랭킹을 조회합니다.",
    )
    fun getRankings(
        date: String,
        period: RankingPeriod,
        page: Int,
        size: Int,
    ): ApiResponse<PageResponse<RankingV1Dto.RankingResponse>>
}
