package com.loopers.config.kafka

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.clients.producer.Producer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import java.util.Properties

@Configuration
@Profile("local")
class LocalInMemoryKafkaConfig {
    @Bean
    fun producerFactory(): ProducerFactory<Any, Any> =
        InMemoryKafkaProducerFactory()

    @Bean
    fun consumerFactory(): ConsumerFactory<Any, Any> =
        InMemoryKafkaConsumerFactory()

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<Any, Any>): KafkaTemplate<Any, Any> =
        KafkaTemplate(producerFactory)

    @Bean(KafkaConfig.BATCH_LISTENER)
    fun defaultBatchListenerContainerFactory(
        consumerFactory: ConsumerFactory<Any, Any>,
    ): ConcurrentKafkaListenerContainerFactory<*, *> =
        ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            this.consumerFactory = consumerFactory
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            isBatchListener = true
            setConcurrency(1)
            setAutoStartup(false)
        }
}

class InMemoryKafkaProducerFactory : ProducerFactory<Any, Any> {
    override fun createProducer(): Producer<Any, Any> =
        MockProducer()
}

class InMemoryKafkaConsumerFactory : ConsumerFactory<Any, Any> {
    override fun createConsumer(
        groupId: String?,
        clientIdPrefix: String?,
        clientIdSuffix: String?,
        properties: Properties?,
    ): Consumer<Any, Any> =
        MockConsumer(OffsetResetStrategy.LATEST)

    override fun isAutoCommit(): Boolean = false
}
