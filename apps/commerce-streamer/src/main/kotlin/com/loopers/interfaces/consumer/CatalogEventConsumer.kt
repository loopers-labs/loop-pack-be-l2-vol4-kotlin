package com.loopers.interfaces.consumer

import com.loopers.application.metrics.ProductMetricsService
import com.loopers.config.kafka.KafkaConfig
import com.loopers.config.kafka.KafkaTopics.CATALOG_EVENTS
import com.loopers.interfaces.consumer.message.LikeChangedMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CatalogEventConsumer(
    private val productMetricsService: ProductMetricsService,
) {
    @KafkaListener(
        topics = [CATALOG_EVENTS],
        groupId = "product-metrics",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(messages: List<LikeChangedMessage>, acknowledgment: Acknowledgment) {
        messages.forEach { productMetricsService.applyLikeChanged(it) }
        acknowledgment.acknowledge()
    }
}
