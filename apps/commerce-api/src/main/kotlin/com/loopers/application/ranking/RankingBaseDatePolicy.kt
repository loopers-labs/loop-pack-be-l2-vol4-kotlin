package com.loopers.application.ranking

import com.loopers.domain.ranking.RankingPeriod
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@Component
class RankingBaseDatePolicy {
    fun normalize(period: RankingPeriod, date: LocalDate): LocalDate {
        return when (period) {
            RankingPeriod.DAILY -> date
            RankingPeriod.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            RankingPeriod.MONTHLY -> date.withDayOfMonth(1)
        }
    }
}
