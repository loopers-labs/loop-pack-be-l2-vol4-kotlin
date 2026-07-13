package com.loopers.interfaces.consumer.ranking

import com.loopers.application.ranking.RankingProjectionService
import com.loopers.event.OrderEventMessage
import org.springframework.kafka.support.Acknowledgment

class OrderRankingEventConsumer(
    private val service: RankingProjectionService,
) {
    fun handle(
        messages: List<OrderEventMessage>,
        acknowledgment: Acknowledgment,
    ) {
        acknowledgment.acknowledge()
    }
}
