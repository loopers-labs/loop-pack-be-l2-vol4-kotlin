package com.loopers.config.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter
import org.springframework.util.backoff.FixedBackOff
import java.util.HashMap

@EnableKafka
@Configuration
class KafkaConfig {
    companion object {
        const val BATCH_LISTENER = "BATCH_LISTENER_DEFAULT"
        const val SINGLE_LISTENER = "SINGLE_LISTENER"
        const val SINGLE_LISTENER_WITH_DLT = "SINGLE_LISTENER_WITH_DLT"
        const val OUTBOX_KAFKA_TEMPLATE = "OUTBOX_KAFKA_TEMPLATE"
        const val DLT_KAFKA_TEMPLATE = "DLT_KAFKA_TEMPLATE"

        private const val MAX_POLLING_SIZE = 3000 // read 3000 msg
        private const val FETCH_MIN_BYTES = (1024 * 1024) // 1mb
        private const val FETCH_MAX_WAIT_MS = 5 * 1000 // broker waiting time = 5s
        private const val SESSION_TIMEOUT_MS = 60 * 1000 // session timeout = 1m
        private const val HEARTBEAT_INTERVAL_MS = 20 * 1000 // heartbeat interval = 20s ( 1/3 of session_timeout )
        private const val MAX_POLL_INTERVAL_MS = 2 * 60 * 1000 // max poll interval = 2m
        private const val DLT_RETRY_INTERVAL_MS = 1_000L
        private const val DLT_RETRY_COUNT = 3L
    }

    @Bean
    fun producerFactory(
        kafkaProperties: KafkaProperties,
    ): ProducerFactory<Any, Any> {
        val props: Map<String, Any> = HashMap(kafkaProperties.buildProducerProperties())
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun consumerFactory(
        kafkaProperties: KafkaProperties,
    ): ConsumerFactory<Any, Any> {
        val props: Map<String, Any> = HashMap(kafkaProperties.buildConsumerProperties())
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<Any, Any>): KafkaTemplate<Any, Any> {
        return KafkaTemplate(producerFactory)
    }

    @Bean(OUTBOX_KAFKA_TEMPLATE)
    fun outboxKafkaTemplate(
        kafkaProperties: KafkaProperties,
    ): KafkaTemplate<String, String> {
        val props = HashMap(kafkaProperties.buildProducerProperties())
            .apply {
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
            }
        return KafkaTemplate(DefaultKafkaProducerFactory(props))
    }

    @Bean(DLT_KAFKA_TEMPLATE)
    fun dltKafkaTemplate(
        kafkaProperties: KafkaProperties,
    ): KafkaTemplate<String, ByteArray> {
        val props = HashMap(kafkaProperties.buildProducerProperties())
            .apply {
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
            }
        return KafkaTemplate(DefaultKafkaProducerFactory(props))
    }

    @Bean
    fun jsonMessageConverter(objectMapper: ObjectMapper): ByteArrayJsonMessageConverter {
        return ByteArrayJsonMessageConverter(objectMapper)
    }

    @Bean(SINGLE_LISTENER)
    fun singleRecordListenerContainerFactory(
        kafkaProperties: KafkaProperties,
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties())
        return ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(consumerConfig)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            isBatchListener = false
        }
    }

    @Bean(SINGLE_LISTENER_WITH_DLT)
    fun singleRecordListenerContainerFactoryWithDlt(
        kafkaProperties: KafkaProperties,
        @Qualifier(DLT_KAFKA_TEMPLATE)
        dltKafkaTemplate: KafkaTemplate<String, ByteArray>,
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties())
        val recoverer = DeadLetterPublishingRecoverer(dltKafkaTemplate) { record, _ ->
            TopicPartition("${record.topic()}.DLT", -1)
        }
        val errorHandler = DefaultErrorHandler(
            recoverer,
            FixedBackOff(DLT_RETRY_INTERVAL_MS, DLT_RETRY_COUNT),
        ).apply {
            addNotRetryableExceptions(IllegalArgumentException::class.java)
        }

        return ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(consumerConfig)
            containerProperties.ackMode = ContainerProperties.AckMode.RECORD
            setCommonErrorHandler(errorHandler)
            isBatchListener = false
        }
    }

    @Bean(BATCH_LISTENER)
    fun defaultBatchListenerContainerFactory(
        kafkaProperties: KafkaProperties,
        converter: ByteArrayJsonMessageConverter,
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties())
            .apply {
                put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_POLLING_SIZE)
                put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, FETCH_MIN_BYTES)
                put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FETCH_MAX_WAIT_MS)
                put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, SESSION_TIMEOUT_MS)
                put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_INTERVAL_MS)
                put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, MAX_POLL_INTERVAL_MS)
            }

        return ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(consumerConfig)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            setBatchMessageConverter(BatchMessagingMessageConverter(converter))
            setConcurrency(3)
            isBatchListener = true
        }
    }
}
