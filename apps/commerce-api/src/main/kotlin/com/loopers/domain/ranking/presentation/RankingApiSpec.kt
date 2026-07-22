package com.loopers.domain.ranking.presentation

import com.loopers.domain.ranking.presentation.response.RankingResponse
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Min

@Tag(name = "Ranking API", description = "Loopers 상품 랭킹 API 입니다.")
interface RankingApiSpec {
    @Operation(
        summary = "일간 상품 랭킹 조회",
        description = "지정한 날짜(yyyyMMdd, 미지정 시 오늘)의 상품 랭킹을 페이지로 조회합니다.",
    )
    fun findRankings(
        date: String?,
        @Min(0)
        page: Int?,
        @Min(1)
        size: Int?,
    ): ApiResponse<PageResponse<RankingResponse>>
}
