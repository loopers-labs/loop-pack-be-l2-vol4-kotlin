package com.loopers.interfaces.consumer

import com.loopers.application.ranking.RankingService
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class RankingConsumer(
    private val rankingService: RankingService,
) {
    @KafkaListener(
        topics = ["catalog-events", "order-events"],
        groupId = GROUP_ID,
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        records: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        rankingService.apply(records.map { String(it.value(), Charsets.UTF_8) })
        acknowledgment.acknowledge()
    }

    companion object {
        // metrics 그룹(loopers-default-consumer)과 분리 — 오프셋·장애·재소비 독립(스펙 D4)
        const val GROUP_ID = "loopers-ranking-consumer"
    }
}
