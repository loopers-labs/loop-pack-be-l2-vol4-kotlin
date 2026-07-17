package com.loopers.interfaces.consumer

import com.loopers.projection.ranking.application.RankingKey
import com.loopers.testcontainers.KafkaTestContainer
import com.loopers.testcontainers.RedisTestContainerInitializer
import com.loopers.utils.DatabaseCleanUp
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
@ContextConfiguration(initializers = [RedisTestContainerInitializer::class])
class RankingKafkaEndToEndTest
    @Autowired
    constructor(
        private val kafkaTemplate: KafkaTemplate<Any, Any>,
        private val redissonClient: RedissonClient,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
            redissonClient.keys.flushdb()
        }

        @Test
        fun `좋아요_3건과_결제완료_주문_1건을_소비하면_주문_상품이_상위_랭킹이_된다`() {
            val 좋아요_상품_ID = 1001L
            val 주문_상품_ID = 2002L
            repeat(3) {
                kafkaTemplate.send(
                    catalogTopic,
                    좋아요_상품_ID.toString(),
                    ProductMetricsKafkaEvent(
                        eventId = UUID.randomUUID(),
                        eventType = RankingEventConsumer.LIKE_COUNT_CHANGED_EVENT_TYPE,
                        aggregateType = "PRODUCT",
                        aggregateId = 좋아요_상품_ID,
                        payload = """{"productId":$좋아요_상품_ID,"userId":${100 + it},"delta":1}""",
                        createdAt = ZonedDateTime.now().toString(),
                    ),
                ).get(10, TimeUnit.SECONDS)
            }
            kafkaTemplate.send(
                orderTopic,
                "900",
                ProductMetricsKafkaEvent(
                    eventId = UUID.randomUUID(),
                    eventType = RankingEventConsumer.ORDER_PAID_EVENT_TYPE,
                    aggregateType = "ORDER",
                    aggregateId = 900L,
                    payload = """{"orderId":900,"items":[{"productId":$주문_상품_ID,"quantity":2}]}""",
                    createdAt = ZonedDateTime.now().toString(),
                ),
            ).get(10, TimeUnit.SECONDS)

            await().atMost(Duration.ofSeconds(15)).untilAsserted {
                assertThat(todayScore(좋아요_상품_ID)).isEqualTo(3.0)
                assertThat(todayScore(주문_상품_ID)).isEqualTo(4.0)
                assertThat(todayRevRank(주문_상품_ID)).isEqualTo(0)
                assertThat(todayRevRank(좋아요_상품_ID)).isEqualTo(1)
                assertThat(todaySet().remainTimeToLive()).isPositive()
            }
        }

        private fun todaySet() = redissonClient.getScoredSortedSet<String>(
            RankingKey.daily(LocalDate.now(RankingKey.ZONE)),
        )

        private fun todayScore(productId: Long): Double? = todaySet().getScore(productId.toString())

        private fun todayRevRank(productId: Long): Int? = todaySet().revRank(productId.toString())

        companion object {
            private val topicSuffix = UUID.randomUUID().toString()
            private val catalogTopic = "catalog-events-ranking-e2e-$topicSuffix"
            private val orderTopic = "order-events-ranking-e2e-$topicSuffix"

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
                registry.add("demo-kafka.test.topic-name") { "demo-ranking-e2e-$topicSuffix" }
            }
        }
    }
