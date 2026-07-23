package com.loopers.domain.ranking

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

enum class RankingPeriod {
    DAILY {
        override fun key(date: LocalDate): String = "ranking:" + date.format(DAILY_FORMAT)
    },
    WEEKLY {
        override fun key(date: LocalDate): String =
            "ranking:weekly:" + "%d-W%02d".format(
                date.get(IsoFields.WEEK_BASED_YEAR),
                date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            )
    },
    MONTHLY {
        override fun key(date: LocalDate): String = "ranking:monthly:" + date.format(MONTHLY_FORMAT)
    }, ;

    abstract fun key(date: LocalDate): String

    companion object {
        private val DAILY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val MONTHLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}