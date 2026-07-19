package com.loopers.interfaces.consumer

import com.loopers.application.order.OrderEventService
import com.loopers.config.kafka.KafkaConfig
import com.loopers.event.OrderEventMessage
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.retrytopic.DltStrategy
import org.springframework.kafka.support.Acknowledgment
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Component

@Component
class OrderEventConsumer(
    private val orderEventService: OrderEventService,
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
        topics = ["\${commerce.events.order-topic:order-events}"],
        containerFactory = KafkaConfig.SINGLE_LISTENER,
    )
    fun handle(
        message: OrderEventMessage,
        acknowledgment: Acknowledgment,
    ) {
        orderEventService.project(message)
        acknowledgment.acknowledge()
    }
}
