package com.loopers.interfaces.consumer

import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class DemoKafkaConsumer {
    @KafkaListener(
        topics = ["\${demo-kafka.test.topic-name}"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun receive(
        messages: List<ConsumerRecord<Any, Any>>,
        acknowledgment: Acknowledgment,
    ) {
        acknowledgment.acknowledge() // manual ack
    }
}
