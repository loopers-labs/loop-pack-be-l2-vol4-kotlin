package com.loopers.domain.ranking.presentation

import com.loopers.domain.ranking.application.RankingFacade
import com.loopers.domain.ranking.application.command.RankingSearchCommand
import com.loopers.domain.ranking.presentation.response.RankingResponse
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/rankings")
@Validated
class RankingController(
    private val rankingFacade: RankingFacade,
) : RankingApiSpec {
    @GetMapping
    override fun findRankings(
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false) page: Int?,
        @RequestParam(required = false) size: Int?,
    ): ApiResponse<PageResponse<RankingResponse>> {
        return rankingFacade.findRankings(RankingSearchCommand.of(date = date, page = page, size = size))
            .map { RankingResponse.from(it) }
            .let { ApiResponse.success(PageResponse.from(it)) }
    }
}
