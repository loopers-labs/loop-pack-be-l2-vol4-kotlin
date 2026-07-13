package com.loopers.interfaces.api.ranking.controller

import com.loopers.application.ranking.RankingFacade
import com.loopers.application.ranking.dto.RankingQuery
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.ranking.RankingV1ApiSpec
import com.loopers.interfaces.api.ranking.dto.RankingV1Dto
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val rankingFacade: RankingFacade,
) : RankingV1ApiSpec {
    @GetMapping
    override fun getRankings(
        @RequestParam date: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<RankingV1Dto.RankingResponse>> {
        val rankingPage = rankingFacade.getRankings(
            RankingQuery(
                date = parseDate(date),
                page = page,
                size = size,
            ),
        )

        return rankingPage
            .map(RankingV1Dto.RankingResponse::from)
            .let(PageResponse<RankingV1Dto.RankingResponse>::from)
            .let(ApiResponse<PageResponse<RankingV1Dto.RankingResponse>>::success)
    }

    private fun parseDate(value: String): LocalDate {
        return try {
            LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
        } catch (_: DateTimeParseException) {
            throw CoreException(ErrorType.BAD_REQUEST, "date must be yyyyMMdd.")
        }
    }
}
