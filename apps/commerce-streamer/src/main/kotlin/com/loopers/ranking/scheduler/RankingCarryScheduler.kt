package com.loopers.ranking.scheduler

import com.loopers.ranking.infrastructure.RankingZSetRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class RankingCarryScheduler(
    private val rankingZSetRepository: RankingZSetRepository,
    private val clock: Clock,
) {
    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    fun snapshotNextDay() {
        rankingZSetRepository.snapshotToNextDay(LocalDate.now(clock))
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun mergeTail() {
        rankingZSetRepository.mergeTailIntoNextDay(LocalDate.now(clock).minusDays(1))
    }
}
