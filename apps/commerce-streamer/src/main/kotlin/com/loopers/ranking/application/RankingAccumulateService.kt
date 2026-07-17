package com.loopers.ranking.application

import com.loopers.ranking.domain.RankingKeys
import com.loopers.ranking.domain.ScoreChange
import com.loopers.ranking.infrastructure.RankingZSetRepository
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.ZonedDateTime

@Service
class RankingAccumulateService(
    private val rankingZSetRepository: RankingZSetRepository,
    private val clock: Clock,
) {
    fun accumulate(eventId: String, occurredAt: Instant, changes: List<ScoreChange>) {
        if (changes.isEmpty()) {
            return
        }
        val eventDate = occurredAt.atZone(RankingKeys.KST).toLocalDate()
        val now = ZonedDateTime.now(clock)
        val recordTail = RankingKeys.isTailWindow(now) && now.withZoneSameInstant(RankingKeys.KST).toLocalDate() == eventDate
        rankingZSetRepository.accumulate(eventId, eventDate, recordTail, changes)
    }
}
