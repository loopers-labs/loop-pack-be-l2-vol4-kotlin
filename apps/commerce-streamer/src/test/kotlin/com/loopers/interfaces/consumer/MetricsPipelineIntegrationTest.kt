package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.ProductMetricsService
import com.loopers.domain.eventhandled.EventHandledRepository
import com.loopers.domain.metrics.ProductMetricsRepository
import com.loopers.testcontainers.KafkaTestContainer
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MetricsPipelineIntegrationTest @Autowired constructor(
    private val productMetricsService: ProductMetricsService,
    private val productMetricsRepository: ProductMetricsRepository,
    private val eventHandledRepository: EventHandledRepository,
    private val objectMapper: ObjectMapper,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private val GROUP = "metrics-test-${UUID.randomUUID()}"

        @JvmStatic
        @DynamicPropertySource
        fun kafkaProps(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { KafkaTestContainer.bootstrapServers }
            registry.add("metrics.consumer.group") { GROUP }
            registry.add("spring.kafka.properties.auto.offset.reset") { "earliest" }
        }
    }

    private lateinit var producer: KafkaProducer<String, ByteArray>

    @BeforeAll
    fun setUp() {
        AdminClient.create(
            mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to KafkaTestContainer.bootstrapServers),
        ).use { admin ->
            listOf("catalog-events", "order-events", "demo.internal.topic-v1").forEach { topic ->
                try {
                    admin.createTopics(listOf(NewTopic(topic, 3, 1.toShort()))).all().get()
                } catch (e: ExecutionException) {
                    if (e.cause !is TopicExistsException) throw e
                }
            }
        }
        producer = KafkaProducer(
            mapOf(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to KafkaTestContainer.bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @AfterAll
    fun tearDownAll() {
        producer.close()
    }

    @DisplayName("catalog-events 의 ProductLiked 를 소비하면, 상품 좋아요 수가 집계된다.")
    @Test
    fun consumesProductLiked_aggregatesLikeCount() {
        // arrange
        val productId = 101L
        val message = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "eventType" to "ProductLiked",
            "aggregateId" to productId,
            "occurredAt" to "2026-07-03T10:00:00+09:00",
            "payload" to mapOf("userId" to 1L, "productId" to productId),
        )

        // act
        send("catalog-events", productId.toString(), message)

        // assert
        await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            val metrics = productMetricsRepository.findByProductId(productId)
            assertThat(metrics?.likeCount).isEqualTo(1L)
        }
        assertThat(eventHandledRepository.existsByEventId(message["eventId"] as String)).isTrue()
    }

    @DisplayName("order-events 의 OrderCreated 를 소비하면, 상품별 판매 수량이 집계된다.")
    @Test
    fun consumesOrderCreated_aggregatesSalesCount() {
        // arrange
        val orderId = 5001L
        val productId = 202L
        val message = mapOf(
            "eventId" to UUID.randomUUID().toString(),
            "eventType" to "OrderCreated",
            "aggregateId" to orderId,
            "occurredAt" to "2026-07-03T10:00:00+09:00",
            "payload" to mapOf(
                "orderId" to orderId,
                "userId" to 1L,
                "items" to listOf(mapOf("productId" to productId, "quantity" to 3, "lineTotal" to 30_000L)),
            ),
        )

        // act
        send("order-events", orderId.toString(), message)

        // assert
        await().atMost(20, TimeUnit.SECONDS).untilAsserted {
            val metrics = productMetricsRepository.findByProductId(productId)
            assertThat(metrics?.salesCount).isEqualTo(3L)
        }
    }

    @DisplayName("같은 이벤트를 두 번 처리해도, 집계는 한 번만 반영된다 (멱등).")
    @Test
    fun isIdempotent_whenSameEventHandledTwice() {
        // arrange
        val productId = 303L
        val eventId = UUID.randomUUID().toString()
        val message = EventMessage(
            eventId = eventId,
            eventType = "ProductLiked",
            aggregateId = productId,
            payload = objectMapper.createObjectNode(),
        )

        // act: 동일 이벤트 두 번 처리
        productMetricsService.handle(message)
        productMetricsService.handle(message)

        // assert: 좋아요 수는 1
        assertThat(productMetricsRepository.findByProductId(productId)?.likeCount).isEqualTo(1L)
    }

    private fun send(topic: String, key: String, message: Map<String, Any?>) {
        producer.send(ProducerRecord(topic, key, objectMapper.writeValueAsBytes(message))).get()
    }
}
