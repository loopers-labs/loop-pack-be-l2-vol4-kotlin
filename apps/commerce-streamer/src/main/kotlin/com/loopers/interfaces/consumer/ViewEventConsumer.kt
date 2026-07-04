package com.loopers.interfaces.consumer

import com.loopers.application.metrics.ProductMetricsService
import com.loopers.config.kafka.KafkaConfig
import com.loopers.config.kafka.KafkaTopics.VIEW_EVENTS
import com.loopers.interfaces.consumer.message.ProductViewedMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class ViewEventConsumer(
    private val productMetricsService: ProductMetricsService,
) {
    @KafkaListener(
        topics = [VIEW_EVENTS],
        groupId = "product-metrics",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(messages: List<ProductViewedMessage>, acknowledgment: Acknowledgment) {
        messages.forEach { productMetricsService.applyViewed(it) }
        acknowledgment.acknowledge()
    }
}
