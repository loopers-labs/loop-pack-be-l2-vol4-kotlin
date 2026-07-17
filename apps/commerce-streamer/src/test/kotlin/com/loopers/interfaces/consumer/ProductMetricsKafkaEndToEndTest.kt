package com.loopers.interfaces.consumer

import com.loopers.projection.like.infrastructure.persistence.ProcessedKafkaEventJpaId
import com.loopers.projection.like.infrastructure.persistence.ProcessedKafkaEventJpaRepository
import com.loopers.projection.like.infrastructure.persistence.ProductLikeCountJpaRepository
import com.loopers.testcontainers.KafkaTestContainer
import com.loopers.utils.DatabaseCleanUp
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
class ProductMetricsKafkaEndToEndTest
    @Autowired
    constructor(
        private val kafkaTemplate: KafkaTemplate<Any, Any>,
        private val productMetricsRepository: ProductLikeCountJpaRepository,
        private val processedKafkaEventRepository: ProcessedKafkaEventJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `Kafka_catalog_event를_소비해_product_metrics와_처리이력을_저장한다`() {
            val eventId = UUID.randomUUID()
            val productId = 1234L
            val event = ProductMetricsKafkaEvent(
                eventId = eventId,
                eventType = LikeCountEventConsumer.PRODUCT_VIEWED_EVENT_TYPE,
                aggregateType = "PRODUCT",
                aggregateId = productId,
                payload = """{"productId":$productId}""",
                createdAt = ZonedDateTime.now().toString(),
            )

            kafkaTemplate.send(catalogTopic, productId.toString(), event).get(10, TimeUnit.SECONDS)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                assertThat(productMetricsRepository.findById(productId)).hasValueSatisfying { metrics ->
                    assertThat(metrics.viewCount).isEqualTo(1)
                    assertThat(metrics.likeCount).isZero()
                    assertThat(metrics.salesCount).isZero()
                }
                assertThat(
                    processedKafkaEventRepository.existsById(
                        ProcessedKafkaEventJpaId(
                            eventId = eventId,
                            consumerGroup = LikeCountEventConsumer.CONSUMER_GROUP,
                        ),
                    ),
                ).isTrue()
            }
        }

        companion object {
            private val topicSuffix = UUID.randomUUID().toString()
            private val catalogTopic = "catalog-events-e2e-$topicSuffix"
            private val orderTopic = "order-events-e2e-$topicSuffix"

            @JvmStatic
            @DynamicPropertySource
            fun kafkaProperties(registry: DynamicPropertyRegistry) {
                val bootstrapServers = KafkaTestContainer.bootstrapServers
                registry.add("spring.kafka.bootstrap-servers") { bootstrapServers }
                registry.add("spring.kafka.admin.properties.bootstrap.servers") { bootstrapServers }
                registry.add("spring.kafka.admin.auto-create") { true }
                registry.add("spring.kafka.consumer.auto-offset-reset") { "earliest" }
                registry.add("spring.kafka.listener.auto-startup") { true }
                registry.add("commerce-events.product-metrics.catalog-topic-name") { catalogTopic }
                registry.add("commerce-events.product-metrics.catalog-dlt-topic-name") { "$catalogTopic.DLT" }
                registry.add("commerce-events.product-metrics.order-topic-name") { orderTopic }
                registry.add("commerce-events.product-metrics.order-dlt-topic-name") { "$orderTopic.DLT" }
                registry.add("commerce-events.product-metrics.partitions") { 1 }
                registry.add("commerce-events.product-metrics.replicas") { 1 }
                registry.add("demo-kafka.test.topic-name") { "demo-e2e-$topicSuffix" }
            }
        }
    }
