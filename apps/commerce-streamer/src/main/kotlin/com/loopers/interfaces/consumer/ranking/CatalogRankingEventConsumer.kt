package com.loopers.interfaces.consumer.ranking

import com.loopers.application.ranking.RankingProjectionService
import com.loopers.event.CatalogEventMessage
import org.springframework.kafka.support.Acknowledgment

class CatalogRankingEventConsumer(
    private val service: RankingProjectionService,
) {
    fun handle(
        messages: List<CatalogEventMessage>,
        acknowledgment: Acknowledgment,
    ) {
        acknowledgment.acknowledge()
    }
}
