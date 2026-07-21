package com.loopers.interfaces.consumer.productmetric

import com.loopers.application.productmetric.ProductMetricDailyEventService
import com.loopers.config.kafka.KafkaConfig
import com.loopers.event.CatalogEventMessage
import com.loopers.event.NonRetryableEventException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class ProductMetricDailyCatalogEventConsumer(
    private val service: ProductMetricDailyEventService,
) {
    @KafkaListener(
        topics = ["\${commerce.events.catalog-topic:catalog-events}"],
        groupId = "\${commerce.product-metric-daily.consumer-group:commerce-product-metric-daily}",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun receive(
        messages: List<CatalogEventMessage>,
        acknowledgment: Acknowledgment,
    ) {
        messages.forEachIndexed { index, message ->
            try {
                service.handle(message)
            } catch (exception: NonRetryableEventException) {
                throw BatchListenerFailedException(exception.message ?: "Invalid product metric catalog event", exception, index)
            }
        }
        acknowledgment.acknowledge()
    }
}
