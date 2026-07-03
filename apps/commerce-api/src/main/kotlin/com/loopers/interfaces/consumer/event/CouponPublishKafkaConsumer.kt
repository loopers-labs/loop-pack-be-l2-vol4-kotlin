package com.loopers.interfaces.consumer.event

import com.loopers.application.event.CouponPublishConsumerService
import com.loopers.application.event.CouponPublishRequestedMessage
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class CouponPublishKafkaConsumer(
    private val couponPublishConsumerService: CouponPublishConsumerService,
) {
    @KafkaListener(
        topics = ["\${event-coupon.kafka.topic-name}"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun listen(
        messages: List<ConsumerRecord<String, CouponPublishRequestedMessage>>,
        acknowledgment: Acknowledgment,
    ) {
        messages.forEach { record ->
            couponPublishConsumerService.process(record.value())
        }
        acknowledgment.acknowledge()
    }
}
