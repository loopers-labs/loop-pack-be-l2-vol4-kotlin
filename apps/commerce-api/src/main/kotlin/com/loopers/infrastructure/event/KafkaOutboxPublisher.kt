package com.loopers.infrastructure.event
import com.loopers.domain.event.OutboxPublisher;import org.springframework.kafka.core.KafkaTemplate;import org.springframework.stereotype.Component
@Component class KafkaOutboxPublisher(private val kafka:KafkaTemplate<Any,Any>):OutboxPublisher{override fun publish(eventId:String,payload:String){kafka.send("order-confirmed",eventId,payload).get()}}
