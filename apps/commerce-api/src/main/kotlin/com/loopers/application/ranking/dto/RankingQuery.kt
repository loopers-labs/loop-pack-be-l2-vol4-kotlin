package com.loopers.application.ranking.dto

import com.loopers.domain.ranking.RankingPeriod
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

data class RankingQuery(
    val date: LocalDate,
    val period: RankingPeriod = RankingPeriod.DAILY,
    val page: Int,
    val size: Int,
) {
    init {
        if (page < 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "page must be zero or greater.")
        }
        if (size !in 1..MAX_PAGE_SIZE) {
            throw CoreException(ErrorType.BAD_REQUEST, "size must be between 1 and $MAX_PAGE_SIZE.")
        }
    }

    private companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
