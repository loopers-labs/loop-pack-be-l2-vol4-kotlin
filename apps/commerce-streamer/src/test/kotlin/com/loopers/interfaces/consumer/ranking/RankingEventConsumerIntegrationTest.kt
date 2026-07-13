package com.loopers.interfaces.consumer.ranking

import com.loopers.config.redis.RankingRedisKeys
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import com.loopers.event.OrderEventItemMessage
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private const val RANKING_BOOTSTRAP_SERVERS = "localhost:19092"
private const val RANKING_CATALOG_TOPIC = "catalog-events-ranking-integration-test"
private const val RANKING_ORDER_TOPIC = "order-events-ranking-integration-test"

@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "commerce.events.catalog-topic=$RANKING_CATALOG_TOPIC",
        "commerce.events.order-topic=$RANKING_ORDER_TOPIC",
        "commerce.ranking.consumer-group=commerce-ranking-integration-test",
        "spring.kafka.bootstrap-servers=$RANKING_BOOTSTRAP_SERVERS",
        "spring.kafka.admin.properties.bootstrap.servers=$RANKING_BOOTSTRAP_SERVERS",
        "spring.kafka.consumer.auto-offset-reset=earliest",
    ],
)
class RankingEventConsumerIntegrationTest
    @Autowired
    constructor(
        private val kafkaTemplate: KafkaTemplate<Any, Any>,
        private val redisTemplate: RedisTemplate<String, String>,
        private val redisCleanUp: RedisCleanUp,
    ) {
        @BeforeEach
        fun setUp() {
            createTopicIfAbsent(RANKING_CATALOG_TOPIC)
            createTopicIfAbsent(RANKING_ORDER_TOPIC)
            redisCleanUp.truncateAll()
        }

        @DisplayName("Kafka batch consumer가 좋아요와 결제 이벤트를 Redis 랭킹에 반영한다")
        @Test
        fun consumesEventsAndProjectsWeightedRanking() {
            val occurredAt = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            val date = occurredAt.toLocalDate()
            val likedProductId = 101L
            val soldProductId = 202L
            repeat(3) {
                publishCatalog(likedMessage(productId = likedProductId, occurredAt = occurredAt))
            }
            publishOrder(soldMessage(productId = soldProductId, occurredAt = occurredAt))

            eventually {
                val likedScore = redisTemplate.opsForZSet()
                    .score(RankingRedisKeys.all(date), likedProductId.toString())
                val soldScore = redisTemplate.opsForZSet()
                    .score(RankingRedisKeys.all(date), soldProductId.toString())
                val soldRank = redisTemplate.opsForZSet()
                    .reverseRank(RankingRedisKeys.all(date), soldProductId.toString())

                assertAll(
                    { assertThat(likedScore ?: Double.NaN).isCloseTo(1.2, within(0.000_001)) },
                    { assertThat(soldScore ?: Double.NaN).isGreaterThan(likedScore ?: Double.MAX_VALUE) },
                    { assertThat(soldRank ?: -1L).isZero() },
                )
            }
        }

        private fun publishCatalog(message: CatalogEventMessage) {
            kafkaTemplate.send(RANKING_CATALOG_TOPIC, message.productId.toString(), message)
                .get(5, TimeUnit.SECONDS)
        }

        private fun publishOrder(message: OrderEventMessage) {
            kafkaTemplate.send(RANKING_ORDER_TOPIC, message.orderId.toString(), message)
                .get(5, TimeUnit.SECONDS)
        }

        private fun likedMessage(productId: Long, occurredAt: ZonedDateTime): CatalogEventMessage {
            return CatalogEventMessage(
                eventId = UUID.randomUUID().toString(),
                eventType = CatalogEventType.PRODUCT_LIKED,
                aggregateId = productId,
                productId = productId,
                brandId = 1L,
                memberId = 1L,
                version = System.nanoTime(),
                occurredAt = occurredAt,
            )
        }

        private fun soldMessage(productId: Long, occurredAt: ZonedDateTime): OrderEventMessage {
            val orderId = System.nanoTime()
            return OrderEventMessage(
                eventId = UUID.randomUUID().toString(),
                eventType = OrderEventType.PAYMENT_SUCCEEDED,
                aggregateId = orderId,
                orderId = orderId,
                orderNumber = "order-$orderId",
                memberId = 1L,
                paymentId = orderId + 1,
                amount = 10L,
                items = listOf(OrderEventItemMessage(productId = productId, quantity = 1L, unitPrice = 10L)),
                occurredAt = occurredAt,
            )
        }

        private fun createTopicIfAbsent(topic: String) {
            AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to RANKING_BOOTSTRAP_SERVERS)).use { adminClient ->
                runCatching {
                    adminClient.createTopics(listOf(NewTopic(topic, 3, 1))).all().get(5, TimeUnit.SECONDS)
                }.onFailure { throwable ->
                    val cause = (throwable as? ExecutionException)?.cause
                    if (cause !is TopicExistsException) {
                        throw throwable
                    }
                }
            }
        }

        private fun eventually(
            timeout: Duration = Duration.ofSeconds(15),
            assertion: () -> Unit,
        ) {
            val deadline = System.nanoTime() + timeout.toNanos()
            var lastError: AssertionError? = null

            while (System.nanoTime() < deadline) {
                try {
                    assertion()
                    return
                } catch (error: AssertionError) {
                    lastError = error
                    Thread.sleep(200)
                }
            }

            throw lastError ?: AssertionError("Condition was not met within $timeout")
        }
    }
