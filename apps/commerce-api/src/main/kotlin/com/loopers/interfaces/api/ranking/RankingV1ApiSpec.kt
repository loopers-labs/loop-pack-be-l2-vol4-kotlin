package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.RequestParam

@Tag(name = "Ranking V1 API", description = "랭킹 API")
interface RankingV1ApiSpec {
    @Operation(summary = "랭킹 조회", description = "기간별(daily/weekly/monthly) 랭킹을 조회합니다.")
    fun getRankings(
        @RequestParam(required = false) period: String?,
        @RequestParam(required = false) date: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>>
}
