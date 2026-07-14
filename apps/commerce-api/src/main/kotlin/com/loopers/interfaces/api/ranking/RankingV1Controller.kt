package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val rankingFacade: RankingFacade,
) : RankingV1ApiSpec {
    @GetMapping
    override fun getRankings(
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "yyyyMMdd")
        date: LocalDate?,
        pageable: Pageable,
    ): ApiResponse<Page<RankingV1Dto.RankingResponse>> {
        val page = rankingFacade.getRankings(date, pageable)
            .map { RankingV1Dto.RankingResponse.from(it) }
        return ApiResponse.success(page)
    }
}
