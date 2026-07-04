package com.loopers.interfaces.consumer

import com.loopers.application.metrics.ProductMetricsService
import com.loopers.config.kafka.KafkaConfig
import com.loopers.config.kafka.KafkaTopics.ORDER_EVENTS
import com.loopers.interfaces.consumer.message.OrderConfirmedMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer(
    private val productMetricsService: ProductMetricsService,
) {
    @KafkaListener(
        topics = [ORDER_EVENTS],
        groupId = "product-metrics",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(messages: List<OrderConfirmedMessage>, acknowledgment: Acknowledgment) {
        messages.forEach { productMetricsService.applyOrderConfirmed(it) }
        acknowledgment.acknowledge()
    }
}
