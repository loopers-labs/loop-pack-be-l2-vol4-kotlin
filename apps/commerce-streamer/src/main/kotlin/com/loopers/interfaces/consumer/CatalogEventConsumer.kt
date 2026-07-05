package com.loopers.interfaces.consumer

import com.loopers.application.catalog.CatalogEventProjectionService
import com.loopers.config.kafka.KafkaConfig
import com.loopers.event.CatalogEventMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.DltStrategy
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import org.springframework.retry.annotation.Backoff

@Component
class CatalogEventConsumer(
    private val catalogEventProjectionService: CatalogEventProjectionService,
) {
    @RetryableTopic(
        attempts = "3",
        backoff = Backoff(delay = 1000),
        dltStrategy = DltStrategy.FAIL_ON_ERROR,
        retryTopicSuffix = "-retry",
        dltTopicSuffix = "-dlt",
        kafkaTemplate = "kafkaTemplate",
    )
    @KafkaListener(
        topics = ["\${commerce.events.catalog-topic:catalog-events}"],
        containerFactory = KafkaConfig.SINGLE_LISTENER,
    )
    fun handle(
        message: CatalogEventMessage,
        acknowledgment: Acknowledgment,
    ) {
        catalogEventProjectionService.project(message)
        acknowledgment.acknowledge()
    }
}
