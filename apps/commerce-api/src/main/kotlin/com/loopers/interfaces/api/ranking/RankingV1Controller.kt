package com.loopers.interfaces.api.ranking

import com.loopers.domain.ranking.RankingQueryService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 랭킹 API.
 * 일간(Redis ZSET), 주간/월간(MV) 인기 상품 랭킹을 제공한다.
 */
@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val rankingQueryService: RankingQueryService,
) {

    /**
     * 랭킹 페이지 조회.
     *
     * @param period 기간 유형 (DAILY/WEEKLY/MONTHLY, 기본 DAILY)
     * @param date 조회 대상 날짜 (yyyyMMdd). 미지정 시 오늘.
     * @param page 페이지 번호 (0-based, 기본 0)
     * @param size 페이지 크기 (기본 20)
     */
    @GetMapping
    fun getRankings(
        @RequestParam(required = false) period: String?,
        @RequestParam(required = false) date: String?,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "0") page: Int,
    ): ApiResponse<List<RankingV1Dto.RankingResponse>> {
        val rankings = rankingQueryService.getRankingPage(period, date, page, size)
        return ApiResponse.success(rankings.map { RankingV1Dto.RankingResponse.from(it) })
    }
}
