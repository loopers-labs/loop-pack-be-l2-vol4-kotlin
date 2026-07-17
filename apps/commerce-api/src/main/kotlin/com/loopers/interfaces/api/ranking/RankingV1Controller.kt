package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.RankingFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.paging.PageCondition
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/v1/rankings")
class RankingV1Controller(
    private val rankingFacade: RankingFacade,
) : RankingV1ApiSpec {
    @GetMapping
    override fun getRankings(
        @RequestParam(required = false) date: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ApiResponse<PageResponse<RankingV1Dto.RankingItemResponse>> {
        val parsedDate = parseDate(date)
        val pageCondition = PageCondition(page = page, size = size)
        val result = rankingFacade.getRankings(parsedDate, pageCondition)
        return ApiResponse.success(
            PageResponse.from(result) { RankingV1Dto.RankingItemResponse.from(it) },
        )
    }

    private fun parseDate(date: String?): LocalDate {
        if (date == null) return LocalDate.now(ZoneId.of("Asia/Seoul"))
        return try {
            LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
        } catch (e: DateTimeParseException) {
            throw CoreException(ErrorType.BAD_REQUEST, "날짜 형식이 올바르지 않습니다. yyyyMMdd 형식이어야 합니다.")
        }
    }
}
