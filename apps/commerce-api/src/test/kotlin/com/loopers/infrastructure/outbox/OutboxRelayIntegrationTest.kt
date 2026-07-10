package com.loopers.infrastructure.outbox

import com.loopers.domain.outbox.KafkaTopics
import com.loopers.domain.outbox.OutboxEvent
import com.loopers.domain.outbox.OutboxEventRepository
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import java.util.UUID

@SpringBootTest
class OutboxRelayIntegrationTest {
    @Autowired lateinit var outboxRepository: OutboxEventRepository

    @Autowired lateinit var outboxRelay: OutboxRelay

    @Autowired lateinit var databaseCleanUp: com.loopers.utils.DatabaseCleanUp

    @Value("\${spring.kafka.bootstrap-servers}")
    lateinit var bootstrap: String

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("PENDING outbox 행을 relayOnce 하면 Kafka로 발행되고 SENT로 전이한다.")
    @Test
    fun relayPublishesAndMarksSent() {
        // arrange
        val saved = outboxRepository.save(
            OutboxEvent(
                eventId = UUID.randomUUID().toString(),
                topic = KafkaTopics.CATALOG_EVENTS,
                partitionKey = "10",
                payload = "{\"type\":\"LIKE_ADDED\",\"productId\":10}",
            ),
        )
        consumer(KafkaTopics.CATALOG_EVENTS).use { c ->
            // act
            val count = outboxRelay.relayOnce()

            // assert: published
            assertThat(count).isEqualTo(1)
            val records = c.poll(Duration.ofSeconds(10))
            assertThat(records.count()).isEqualTo(1)
            val rec = records.iterator().next()
            assertThat(rec.key()).isEqualTo("10")
            assertThat(rec.value()).contains("LIKE_ADDED")
            // assert: marked SENT
            assertThat(outboxRepository.findTopPending(10)).isEmpty()
        }
    }

    private fun consumer(topic: String): KafkaConsumer<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            ConsumerConfig.GROUP_ID_CONFIG to "test-${UUID.randomUUID()}",
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        )
        return KafkaConsumer<String, String>(props).apply { subscribe(listOf(topic)) }
    }
}
