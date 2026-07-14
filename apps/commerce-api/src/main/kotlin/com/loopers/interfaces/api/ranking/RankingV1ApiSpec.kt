package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.RequestParam
import java.time.LocalDate

@Tag(name = "Ranking API", description = "상품 랭킹 대고객 API 입니다.")
interface RankingV1ApiSpec {
    @Operation(
        summary = "랭킹 목록 조회",
        description = "특정 일자(yyyyMMdd)의 상품 랭킹을 페이지로 조회합니다. date 미지정 시 오늘 랭킹을 반환합니다.",
    )
    fun getRankings(
        @Schema(name = "조회 일자", description = "yyyyMMdd 형식, 미지정 시 오늘")
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "yyyyMMdd")
        date: LocalDate?,
        pageable: Pageable,
    ): ApiResponse<Page<RankingV1Dto.RankingResponse>>
}
