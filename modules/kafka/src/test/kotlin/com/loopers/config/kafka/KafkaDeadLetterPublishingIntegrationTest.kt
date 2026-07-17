package com.loopers.config.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.testcontainers.KafkaTestContainer
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.support.serializer.JsonSerializer
import java.time.Duration
import java.util.UUID

class KafkaDeadLetterPublishingIntegrationTest {
    private val bootstrapServers = KafkaTestContainer.bootstrapServers

    @Test
    fun `DLT에_발행한_byte_array_payload는_원본과_동일하다`() {
        val sourceTopic = "raw-byte-source-${UUID.randomUUID()}"
        val deadLetterTopic = "$sourceTopic.DLT"
        createTopic(deadLetterTopic)
        val originalPayload = """{"eventId":"${UUID.randomUUID()}","requestId":"${UUID.randomUUID()}"}"""
            .toByteArray()

        contextRunner().run { context ->
            val recoverer = context.getBean(DeadLetterPublishingRecoverer::class.java)

            recoverer.accept(
                ConsumerRecord(sourceTopic, 0, 0L, "template-1", originalPayload),
                IllegalStateException("retry exhausted"),
            )
        }

        val published = consumeOne(deadLetterTopic)
        assertThat(published.key()).isEqualTo("template-1")
        assertThat(published.value()).isEqualTo(originalPayload)
    }

    private fun contextRunner(): ApplicationContextRunner =
        ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration::class.java))
            .withUserConfiguration(KafkaConfig::class.java)
            .withBean(ObjectMapper::class.java, ::ObjectMapper)
            .withPropertyValues(
                "spring.kafka.bootstrap-servers=$bootstrapServers",
                "spring.kafka.producer.acks=all",
                "spring.kafka.producer.key-serializer=${StringSerializer::class.java.name}",
                "spring.kafka.producer.value-serializer=${JsonSerializer::class.java.name}",
                "spring.kafka.producer.properties.enable.idempotence=true",
                "spring.kafka.consumer.key-deserializer=${StringDeserializer::class.java.name}",
                "spring.kafka.consumer.value-deserializer=${ByteArrayDeserializer::class.java.name}",
            )

    private fun createTopic(topic: String) {
        AdminClient.create(
            mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers),
        ).use { admin ->
            admin.createTopics(listOf(NewTopic(topic, 1, 1.toShort()))).all().get()
        }
    }

    private fun consumeOne(topic: String): ConsumerRecord<String, ByteArray> {
        val partition = TopicPartition(topic, 0)
        val properties = mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to "raw-byte-dlt-${UUID.randomUUID()}",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
        )
        KafkaConsumer<String, ByteArray>(properties).use { consumer ->
            consumer.assign(listOf(partition))
            consumer.seekToBeginning(listOf(partition))
            val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
            while (System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(200)).firstOrNull()?.let { return it }
            }
        }
        error("DLT record was not published to $topic")
    }
}
