package com.loopers.domain.ranking.application.command

import com.loopers.domain.ranking.vo.RankingKey
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class RankingSearchCommand(
    val date: LocalDate,
    val page: Int = DEFAULT_PAGE,
    val size: Int = DEFAULT_SIZE,
) {
    init {
        validatePage(page)
        validateSize(size)
    }

    companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20

        fun of(
            date: String? = null,
            page: Int? = null,
            size: Int? = null,
        ): RankingSearchCommand = RankingSearchCommand(
            date = parseDate(date),
            page = page ?: DEFAULT_PAGE,
            size = size ?: DEFAULT_SIZE,
        )

        private fun parseDate(date: String?): LocalDate {
            if (date == null) {
                return LocalDate.now(RankingKey.ZONE)
            }
            return try {
                LocalDate.parse(date, RankingKey.DATE_FORMATTER)
            } catch (e: DateTimeParseException) {
                throw CoreException(ErrorType.BAD_REQUEST, "날짜 형식은 yyyyMMdd 이어야 합니다.", e)
            }
        }

        private fun validatePage(page: Int) {
            if (page < 0) {
                throw CoreException(ErrorType.BAD_REQUEST, "페이지 번호는 0 이상이어야 합니다.")
            }
        }

        private fun validateSize(size: Int) {
            if (size < 1) {
                throw CoreException(ErrorType.BAD_REQUEST, "페이지 크기는 1 이상이어야 합니다.")
            }
        }
    }
}
