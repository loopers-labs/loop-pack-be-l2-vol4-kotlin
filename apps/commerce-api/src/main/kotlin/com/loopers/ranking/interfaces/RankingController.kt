package com.loopers.ranking.interfaces

import com.loopers.ranking.application.RankingItemInfo
import com.loopers.ranking.application.RankingPageInfo
import com.loopers.ranking.application.RankingQueryService
import com.loopers.ranking.domain.RankingErrorCode
import com.loopers.support.error.BadRequestException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@RestController
@RequestMapping("/api/v1/rankings")
class RankingController(
    private val rankingQueryService: RankingQueryService,
) {
    @GetMapping
    fun getRankings(
        @RequestParam(required = false) date: String?,
        @RequestParam(required = false, defaultValue = "1") page: Int,
        @RequestParam(required = false, defaultValue = "20") size: Int,
    ): RankingResponse {
        if (size !in 1..MAX_PAGE_SIZE) {
            throw BadRequestException(RankingErrorCode.INVALID_PAGE_SIZE)
        }
        if (page < 1) {
            throw BadRequestException(RankingErrorCode.INVALID_PAGE)
        }
        return RankingResponse.from(rankingQueryService.getPage(parseDate(date), page, size))
    }

    private fun parseDate(date: String?): LocalDate? {
        if (date == null) {
            return null
        }
        return try {
            LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE)
        } catch (e: DateTimeParseException) {
            throw BadRequestException(RankingErrorCode.INVALID_DATE_FORMAT)
        }
    }

    private companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}

data class RankingResponse(
    val date: String,
    val page: Int,
    val size: Int,
    val items: List<RankingItemResponse>,
) {
    companion object {
        fun from(info: RankingPageInfo): RankingResponse =
            RankingResponse(
                date = info.date.format(DateTimeFormatter.BASIC_ISO_DATE),
                page = info.page,
                size = info.size,
                items = info.items.map(RankingItemResponse::from),
            )
    }
}

data class RankingItemResponse(
    val rank: Long,
    val productId: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val score: Double,
) {
    companion object {
        fun from(info: RankingItemInfo): RankingItemResponse =
            RankingItemResponse(
                rank = info.rank,
                productId = info.productId,
                name = info.name,
                price = info.price,
                likeCount = info.likeCount,
                score = info.score,
            )
    }
}
