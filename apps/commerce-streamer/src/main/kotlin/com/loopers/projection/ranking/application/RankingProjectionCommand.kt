package com.loopers.projection.ranking.application

import java.time.ZonedDateTime
import java.util.UUID

data class RankingProjectionCommand(
    val eventId: UUID,
    val occurredAt: ZonedDateTime,
    val deltas: List<RankingScoreDelta>,
)
