package com.loopers.interfaces.api.ranking

import com.loopers.domain.ranking.RankingPeriod
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
        description = "기간별(일간/주간/월간) 상품 랭킹을 페이지로 조회합니다. " +
            "period 로 집계 단위를 고르고, date(yyyyMMdd 또는 yyyy-MM-dd)가 속한 일/주/월의 랭킹을 반환합니다. " +
            "date 미지정 시 오늘 기준입니다.",
    )
    fun getRankings(
        @Schema(name = "집계 단위", description = "DAILY | WEEKLY | MONTHLY, 미지정 시 DAILY", defaultValue = "DAILY")
        @RequestParam(required = false, defaultValue = "DAILY")
        period: RankingPeriod,
        @Schema(name = "조회 일자", description = "yyyyMMdd 또는 yyyy-MM-dd 형식, 미지정 시 오늘")
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "yyyyMMdd")
        date: LocalDate?,
        pageable: Pageable,
    ): ApiResponse<Page<RankingV1Dto.RankingResponse>>
}