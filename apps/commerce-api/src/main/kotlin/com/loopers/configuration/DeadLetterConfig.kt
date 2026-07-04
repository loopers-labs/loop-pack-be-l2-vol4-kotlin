package com.loopers.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter
import org.springframework.util.backoff.FixedBackOff

@Configuration
class DeadLetterConfig {
    @Bean(SINGLE_LISTENER)
    fun singleFactory(
        converter: ByteArrayJsonMessageConverter,
        kafkaTemplate: KafkaTemplate<Any, Any>,
        consumerFactory: ConsumerFactory<Any, Any>,
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val recover = DeadLetterPublishingRecoverer(kafkaTemplate)
        val errorHandler = DefaultErrorHandler(recover, FixedBackOff(1000, 3))
        return ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            this.consumerFactory = consumerFactory
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            setRecordMessageConverter(converter)
            setCommonErrorHandler(errorHandler)
        }
    }

    companion object {
        const val SINGLE_LISTENER = "SINGLE_LISTENER_DEFAULT"
    }
}
