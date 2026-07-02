package com.loopers.interfaces.consumer

import com.loopers.application.catalog.CatalogEventProjectionService
import com.loopers.config.kafka.KafkaConfig
import com.loopers.event.CatalogEventMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CatalogEventConsumer(
    private val catalogEventProjectionService: CatalogEventProjectionService,
) {
    @KafkaListener(
        topics = ["\${commerce.events.catalog-topic:catalog-events}"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun handle(
        messages: List<CatalogEventMessage>,
        acknowledgment: Acknowledgment,
    ) {
        messages.forEach(catalogEventProjectionService::project)
        acknowledgment.acknowledge()
    }
}
