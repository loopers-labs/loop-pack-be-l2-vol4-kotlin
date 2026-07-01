package com.loopers.config.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner

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
    fun `kafka_yml_consumer_value_deserializer_설정이_바인딩된다`() {
        contextRunner.run { context ->
            val consumerProperties = context.getBean(KafkaProperties::class.java)
                .buildConsumerProperties()

            assertThat(consumerProperties[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG])
                .isEqualTo(ByteArrayDeserializer::class.java)
        }
    }
}
