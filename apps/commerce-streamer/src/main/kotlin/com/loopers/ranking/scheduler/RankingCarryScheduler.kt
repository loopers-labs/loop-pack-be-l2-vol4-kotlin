package com.loopers.ranking.scheduler

import com.loopers.notification.NotificationSender
import com.loopers.ranking.infrastructure.RankingZSetRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class RankingCarryScheduler(
    private val rankingZSetRepository: RankingZSetRepository,
    private val notificationSender: NotificationSender,
    private val clock: Clock,
) {
    @Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
    fun snapshotNextDay() {
        val today = LocalDate.now(clock)
        try {
            rankingZSetRepository.snapshotToNextDay(today)
        } catch (e: Exception) {
            notificationSender.notify("carry 스냅샷 실패", "date=$today ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    fun mergeTail() {
        val yesterday = LocalDate.now(clock).minusDays(1)
        try {
            rankingZSetRepository.mergeTailIntoNextDay(yesterday)
        } catch (e: Exception) {
            notificationSender.notify("carry tail 병합 실패", "date=$yesterday ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
