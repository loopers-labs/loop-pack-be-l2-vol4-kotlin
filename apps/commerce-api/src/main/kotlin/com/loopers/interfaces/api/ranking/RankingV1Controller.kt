package com.loopers.interfaces.api.ranking

import com.loopers.domain.ranking.RankingQueryService
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 랭킹 API.
 * Redis ZSET 기반으로 일별 인기 상품 랭킹을 제공한다.
 */
@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val rankingQueryService: RankingQueryService,
) {

    /**
     * 랭킹 페이지 조회.
     * date 미지정 시 오늘 날짜 기준으로 조회한다.
     *
     * @param date 조회 대상 날짜 (yyyyMMdd)
     * @param page 페이지 번호 (0-based, 기본 0)
     * @param size 페이지 크기 (기본 20)
     */
    @GetMapping
    fun getRankings(
        @RequestParam(required = false) date: String?,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "0") page: Int,
    ): ApiResponse<List<RankingV1Dto.RankingResponse>> {
        val rankings = rankingQueryService.getRankingPage(date, page, size)
        return ApiResponse.success(rankings.map { RankingV1Dto.RankingResponse.from(it) })
    }
}
