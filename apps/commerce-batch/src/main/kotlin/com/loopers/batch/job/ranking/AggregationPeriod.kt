package com.loopers.batch.job.ranking

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

data class AggregationPeriod(
    val from: LocalDate,
    val to: LocalDate,
    val key: String,
) {
    companion object {
        fun weeklyOf(baseDate: LocalDate): AggregationPeriod {
            val monday = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            val weekBasedYear = baseDate.get(IsoFields.WEEK_BASED_YEAR)
            val week = baseDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

            return AggregationPeriod(
                from = monday,
                to = sunday,
                key = "%d-W%02d".format(weekBasedYear, week),
            )
        }

        fun monthlyOf(baseDate: LocalDate): AggregationPeriod {
            val first = baseDate.withDayOfMonth(1)
            val last = baseDate.withDayOfMonth(baseDate.lengthOfMonth())
            return AggregationPeriod(
                from = first,
                to = last,
                key = "%d-%02d".format(baseDate.year, baseDate.monthValue),
            )
        }
    }
}
