package com.loopers.config

import java.util.HashMap
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter

@Configuration
class CouponKafkaListenerConfig {
    companion object {
        const val COUPON_ISSUE_LISTENER = "COUPON_ISSUE_LISTENER"
    }

    @Bean(COUPON_ISSUE_LISTENER)
    fun couponIssueListenerContainerFactory(
        kafkaProperties: KafkaProperties,
        converter: ByteArrayJsonMessageConverter,
    ): ConcurrentKafkaListenerContainerFactory<Any, Any> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties())
            .apply {
                put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
                put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java)
            }

        return ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(consumerConfig)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            setRecordMessageConverter(converter)
            setConcurrency(1)
        }
    }
}
