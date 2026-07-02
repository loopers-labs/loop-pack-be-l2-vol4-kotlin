package com.loopers.config.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.test.util.ReflectionTestUtils

class KafkaConfigBindingTest {
    private val contextRunner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withConfiguration(
            AutoConfigurations.of(
                KafkaAutoConfiguration::class.java,
            ),
        )
        .withUserConfiguration(KafkaConfig::class.java)
        .withBean(ObjectMapper::class.java, ::ObjectMapper)
        .withPropertyValues(
            "spring.config.import=classpath:kafka.yml",
            "spring.profiles.active=test",
            "spring.application.name=kafka-config-test",
            "BOOTSTRAP_SERVERS=localhost:19092",
        )

    @Test
    fun `kafka_yml_producer_consumer_serializer_설정이_바인딩된다`() {
        contextRunner.run { context ->
            val kafkaProperties = context.getBean(KafkaProperties::class.java)
            val producerProperties = kafkaProperties.buildProducerProperties()
            val consumerProperties = kafkaProperties.buildConsumerProperties()

            assertThat(kafkaProperties.properties["auto.create.topics.enable"])
                .isEqualTo("false")
            assertThat(producerProperties[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG])
                .isEqualTo(StringSerializer::class.java)
            assertThat(producerProperties[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG])
                .isEqualTo(JsonSerializer::class.java)
            assertThat(consumerProperties[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG])
                .isEqualTo(StringDeserializer::class.java)
            assertThat(consumerProperties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG])
                .isEqualTo(ByteArrayDeserializer::class.java)
        }
    }

    @Test
    fun `배치_리스너는_기존_수동_ack_batch_동작을_유지한다`() {
        contextRunner.run { context ->
            val factory = context.getBean(
                KafkaConfig.BATCH_LISTENER,
                ConcurrentKafkaListenerContainerFactory::class.java,
            )

            assertThat(factory.isBatchListener).isTrue()
            assertThat(factory.containerProperties.ackMode)
                .isEqualTo(ContainerProperties.AckMode.MANUAL)
        }
    }

    @Test
    fun `레코드_리스너는_수동_ack와_DLT_재시도_핸들러를_사용한다`() {
        contextRunner.run { context ->
            val factory = context.getBean(
                KafkaConfig.RECORD_LISTENER,
                ConcurrentKafkaListenerContainerFactory::class.java,
            )
            val errorHandler = context.getBean(DefaultErrorHandler::class.java)

            assertThat(factory.isBatchListener).isFalse()
            assertThat(factory.containerProperties.ackMode)
                .isEqualTo(ContainerProperties.AckMode.MANUAL)
            assertThat(context).hasSingleBean(DeadLetterPublishingRecoverer::class.java)
            assertThat(context).hasSingleBean(DefaultErrorHandler::class.java)
            assertThat(ReflectionTestUtils.getField(factory, "commonErrorHandler"))
                .isSameAs(errorHandler)
        }
    }
}
