package com.loopers.interfaces.consumer

import com.loopers.domain.useraction.UserActionType
import com.loopers.event.OrderEventItemMessage
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import com.loopers.infrastructure.event.repository.EventHandledJpaRepository
import com.loopers.infrastructure.product.repository.ProductStatJpaRepository
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

private const val ORDER_EVENT_BOOTSTRAP_SERVERS = "localhost:19092"
private const val ORDER_EVENT_CATALOG_TOPIC = "catalog-events-order-integration-test"
private const val ORDER_EVENT_ORDER_TOPIC = "order-events-integration-test"
private const val ORDER_EVENT_CONSUMER_GROUP = "commerce-streamer-order-integration-test"
private const val ORDER_EVENT_HANDLED_GROUP = "loopers-default-consumer"

@Import(MySqlTestContainersConfig::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "commerce.events.catalog-topic=$ORDER_EVENT_CATALOG_TOPIC",
        "commerce.events.order-topic=$ORDER_EVENT_ORDER_TOPIC",
        "spring.kafka.bootstrap-servers=$ORDER_EVENT_BOOTSTRAP_SERVERS",
        "spring.kafka.admin.properties.bootstrap.servers=$ORDER_EVENT_BOOTSTRAP_SERVERS",
        "spring.kafka.consumer.group-id=$ORDER_EVENT_CONSUMER_GROUP",
        "spring.kafka.consumer.auto-offset-reset=earliest",
    ],
)
class OrderEventConsumerIntegrationTest
    @Autowired
    constructor(
        private val kafkaTemplate: KafkaTemplate<Any, Any>,
        private val databaseCleanUp: DatabaseCleanUp,
        private val userActionLogJpaRepository: UserActionLogJpaRepository,
        private val eventHandledJpaRepository: EventHandledJpaRepository,
        private val productStatJpaRepository: ProductStatJpaRepository,
    ) {
        @BeforeEach
        fun setUp() {
            createTopicIfAbsent(ORDER_EVENT_CATALOG_TOPIC)
            createTopicIfAbsent(ORDER_EVENT_ORDER_TOPIC)
            databaseCleanUp.truncateAllTables()
        }

        @DisplayName("Kafka order 결제 성공 이벤트를 소비해 유저 행동 로그를 저장한다")
        @Test
        fun consumesPaymentSucceededEventAndRecordsUserActionLog() {
            val message = createMessage(eventType = OrderEventType.PAYMENT_SUCCEEDED)

            kafkaTemplate
                .send(ORDER_EVENT_ORDER_TOPIC, message.orderId.toString(), message)
                .get(5, TimeUnit.SECONDS)

            eventually {
                val userActionLog = userActionLogJpaRepository.findByEventId(message.eventId)
                val eventHandled = eventHandledJpaRepository.findByConsumerGroupAndEventId(
                    consumerGroup = ORDER_EVENT_HANDLED_GROUP,
                    eventId = message.eventId,
                )
                val productStat = productStatJpaRepository.findByProductId(10L)

                assertAll(
                    { assertThat(userActionLog?.eventId).isEqualTo(message.eventId) },
                    { assertThat(userActionLog?.actionType).isEqualTo(UserActionType.PAYMENT_SUCCEEDED) },
                    { assertThat(userActionLog?.aggregateId).isEqualTo(message.orderId) },
                    { assertThat(userActionLog?.productId).isNull() },
                    { assertThat(productStat?.salesCount).isEqualTo(2L) },
                    { assertThat(eventHandled?.eventId).isEqualTo(message.eventId) },
                )
            }
        }

        private fun createMessage(eventType: OrderEventType): OrderEventMessage {
            val orderId = System.nanoTime()
            return OrderEventMessage(
                eventId = UUID.randomUUID().toString(),
                eventType = eventType,
                aggregateId = orderId,
                orderId = orderId,
                orderNumber = "order-$orderId",
                memberId = 1L,
                paymentId = orderId + 1,
                amount = 10_000L,
                items = listOf(OrderEventItemMessage(productId = 10L, quantity = 2L, unitPrice = 1_000L)),
                occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
            )
        }

        private fun createTopicIfAbsent(topic: String) {
            AdminClient.create(
                mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to ORDER_EVENT_BOOTSTRAP_SERVERS),
            ).use { adminClient ->
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
