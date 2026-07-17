package com.loopers.infrastructure.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

/**
 * commerce-streamer Kafka Consumer 설정.
 * 단건 처리 + Manual Ack 방식으로 이벤트를 안전하게 소비한다.
 */
@Configuration
class KafkaConsumerConfig {

    /**
     * 단건 처리용 리스너 컨테이너 팩토리.
     * - max.poll.records = 1: 한 번에 1건씩 처리하여 순서 보장
     * - enable.auto.commit = false: 수동 커밋으로 처리 완료 후에만 offset 이동
     * - ackMode = MANUAL: 애플리케이션 코드에서 명시적으로 ack
     */
    @Bean
    fun singleListenerContainerFactory(
        kafkaProperties: KafkaProperties,
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val props = HashMap(kafkaProperties.buildConsumerProperties()).apply {
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1)
        }

        return ConcurrentKafkaListenerContainerFactory<String, String>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(props)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            setConcurrency(1)
        }
    }
}
