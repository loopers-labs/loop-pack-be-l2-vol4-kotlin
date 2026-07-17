package com.loopers.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.TopicPartition
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter
import org.springframework.util.backoff.FixedBackOff

@Configuration
class StreamerKafkaConfig {
    companion object {
        const val BATCH_LISTENER_DLQ = "BATCH_LISTENER_DLQ"
        const val BATCH_LISTENER_FALLBACK = "BATCH_LISTENER_FALLBACK"

        private const val RETRY_INTERVAL_MS = 1_000L
        private const val RETRY_COUNT = 3L

        private const val FALLBACK_MAX_POLL_RECORDS = 500
        private const val FALLBACK_FETCH_MAX_WAIT_MS = 500
        private const val FALLBACK_FETCH_MIN_BYTES = 1
    }

    @Bean
    fun dltErrorHandler(kafkaTemplate: KafkaTemplate<Any, Any>): DefaultErrorHandler {
        val recoverer = DeadLetterPublishingRecoverer(kafkaTemplate) { record, _ ->
            TopicPartition("${record.topic()}-dlt", -1)
        }
        return DefaultErrorHandler(recoverer, FixedBackOff(RETRY_INTERVAL_MS, RETRY_COUNT))
    }

    @Bean(BATCH_LISTENER_DLQ)
    fun dlqBatchListenerContainerFactory(
        kafkaProperties: KafkaProperties,
        converter: ByteArrayJsonMessageConverter,
        dltErrorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties())
        return ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(consumerConfig)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            setBatchMessageConverter(BatchMessagingMessageConverter(converter))
            setConcurrency(3)
            isBatchListener = true
            setCommonErrorHandler(dltErrorHandler)
        }
    }

    @Bean(BATCH_LISTENER_FALLBACK)
    fun fallbackBatchListenerContainerFactory(
        kafkaProperties: KafkaProperties,
        converter: ByteArrayJsonMessageConverter,
        dltErrorHandler: DefaultErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties())
            .apply {
                put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, FALLBACK_MAX_POLL_RECORDS)
                put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FALLBACK_FETCH_MAX_WAIT_MS)
                put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, FALLBACK_FETCH_MIN_BYTES)
            }
        return ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(consumerConfig)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            setBatchMessageConverter(BatchMessagingMessageConverter(converter))
            setConcurrency(3)
            isBatchListener = true
            setCommonErrorHandler(dltErrorHandler)
        }
    }
}
