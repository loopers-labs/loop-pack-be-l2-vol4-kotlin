package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.event.EventTopic
import com.loopers.application.event.PaymentRequestedEvent
import com.loopers.application.payment.PaymentRequestProcessor
import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

@Component
class PaymentRequestedConsumer(
    private val paymentRequestProcessor: PaymentRequestProcessor,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [EventTopic.PAYMENT_EVENTS_VALUE],
        groupId = "payment-request-consumer",
        containerFactory = KafkaConfig.SINGLE_LISTENER,
    )
    fun consume(record: ConsumerRecord<String, ByteArray>, acknowledgment: Acknowledgment) {
        try {
            val payload = objectMapper.readTree(String(record.value(), StandardCharsets.UTF_8))
            val eventType = payload.get("eventType")?.asText()

            if (eventType != PaymentRequestedEvent.EVENT_TYPE) {
                acknowledgment.acknowledge()
                return
            }

            paymentRequestProcessor.process(
                paymentId = payload.get("paymentId").asLong(),
                callbackUrl = payload.get("callbackUrl").asText(),
            )
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            log.error("결제 요청 이벤트 처리 실패: topic={}, offset={}", record.topic(), record.offset(), e)
        }
    }
}
