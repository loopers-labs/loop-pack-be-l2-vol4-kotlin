package com.loopers.interfaces.consumer

import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import com.loopers.infrastructure.event.repository.EventHandledJpaRepository
import com.loopers.infrastructure.product.repository.ProductStatProjectionJpaRepository
import com.loopers.infrastructure.useraction.repository.UserActionLogJpaRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.errors.TopicExistsException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import java.time.Duration
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private const val BOOTSTRAP_SERVERS = "localhost:19092"
private const val CATALOG_TOPIC = "catalog-events-integration-test"

@Import(MySqlTestContainersConfig::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "commerce.events.catalog-topic=$CATALOG_TOPIC",
        "spring.kafka.bootstrap-servers=$BOOTSTRAP_SERVERS",
        "spring.kafka.admin.properties.bootstrap.servers=$BOOTSTRAP_SERVERS",
        "spring.kafka.consumer.group-id=commerce-streamer-integration-test",
        "spring.kafka.consumer.auto-offset-reset=earliest",
    ],
)
class CatalogEventConsumerIntegrationTest
    @Autowired
    constructor(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val productStatProjectionJpaRepository: ProductStatProjectionJpaRepository,
    private val userActionLogJpaRepository: UserActionLogJpaRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    ) {
    @BeforeEach
    fun setUp() {
        createTopicIfAbsent(CATALOG_TOPIC)
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("Kafka catalog 좋아요 이벤트를 소비해 상품 집계와 유저 행동 로그를 저장한다")
    @Test
    fun consumesLikedEventAndProjectsCatalogState() {
        val message = createMessage(eventType = CatalogEventType.PRODUCT_LIKED)

        publish(message)

        eventually {
            val productStat = productStatProjectionJpaRepository.findByProductId(message.productId)
            val userActionLog = userActionLogJpaRepository.findByEventId(message.eventId)
            val eventHandled = eventHandledJpaRepository.findByEventId(message.eventId)

            assertAll(
                { assertThat(productStat?.likeCount).isEqualTo(1L) },
                { assertThat(productStat?.latestEventVersion).isEqualTo(message.version) },
                { assertThat(userActionLog?.eventId).isEqualTo(message.eventId) },
                { assertThat(userActionLog?.productId).isEqualTo(message.productId) },
                { assertThat(eventHandled?.eventId).isEqualTo(message.eventId) },
            )
        }
    }

    @DisplayName("같은 eventId의 Kafka catalog 이벤트는 한 번만 projection 한다")
    @Test
    fun skipsDuplicatedEventByEventId() {
        val message = createMessage(eventType = CatalogEventType.PRODUCT_LIKED)

        publish(message)
        publish(message)

        eventually {
            val productStat = productStatProjectionJpaRepository.findByProductId(message.productId)
            val userActionLog = userActionLogJpaRepository.findByEventId(message.eventId)
            val eventHandled = eventHandledJpaRepository.findByEventId(message.eventId)

            assertAll(
                { assertThat(productStat?.likeCount).isEqualTo(1L) },
                { assertThat(userActionLog?.eventId).isEqualTo(message.eventId) },
                { assertThat(eventHandled?.eventId).isEqualTo(message.eventId) },
            )
        }
    }

    private fun publish(message: CatalogEventMessage) {
        kafkaTemplate
            .send(CATALOG_TOPIC, message.productId.toString(), message)
            .get(5, TimeUnit.SECONDS)
    }

    private fun createMessage(eventType: CatalogEventType): CatalogEventMessage {
        val productId = System.nanoTime()
        return CatalogEventMessage(
            eventId = UUID.randomUUID().toString(),
            eventType = eventType,
            aggregateId = productId,
            productId = productId,
            brandId = 100L,
            memberId = 1L,
            version = productId,
            occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }

    private fun createTopicIfAbsent(topic: String) {
        AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to BOOTSTRAP_SERVERS)).use { adminClient ->
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
        timeout: Duration = Duration.ofSeconds(10),
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
