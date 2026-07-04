package com.loopers.interfaces.consumer

import com.loopers.config.kafka.KafkaTopics
import com.loopers.infrastructure.metrics.ProductMetricRepository
import com.loopers.interfaces.consumer.message.OrderConfirmedMessage
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import java.time.Duration
import java.time.ZonedDateTime

/**
 * order-events 발행 → OrderEventConsumer → product_metrics salesCount 통합 검증.
 * (catalog-events 도 함께 선언해야 CatalogEventConsumer 가 구독 토픽을 찾는다)
 */
@SpringBootTest(properties = ["spring.kafka.properties.auto.offset.reset=earliest"])
@EmbeddedKafka(
    partitions = 3,
    topics = [KafkaTopics.CATALOG_EVENTS, KafkaTopics.ORDER_EVENTS],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class OrderEventConsumerIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val productMetricRepository: ProductMetricRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun salesCountOf(productId: Long): Int? =
        productMetricRepository.findById(productId).orElse(null)?.salesCount

    @DisplayName("주문 확정 메시지를 소비하면 상품별 salesCount 가 수량만큼 증가한다")
    @Test
    fun orderIncrementsSales() {
        val msg = OrderConfirmedMessage(
            eventId = "evt-order-1",
            orderId = 1L,
            items = listOf(
                OrderConfirmedMessage.Item(productId = 10L, quantity = 2),
                OrderConfirmedMessage.Item(productId = 20L, quantity = 3),
            ),
            occurredAt = ZonedDateTime.now(),
        )
        kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, msg.orderId.toString(), msg).get()

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(salesCountOf(10L)).isEqualTo(2)
            assertThat(salesCountOf(20L)).isEqualTo(3)
        }
    }

    @DisplayName("같은 eventId 주문 메시지를 두 번 보내도 멱등하게 한 번만 반영된다")
    @Test
    fun idempotentOnDuplicate() {
        val msg = OrderConfirmedMessage(
            eventId = "evt-order-dup",
            orderId = 2L,
            items = listOf(OrderConfirmedMessage.Item(productId = 30L, quantity = 5)),
            occurredAt = ZonedDateTime.now(),
        )
        kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, msg.orderId.toString(), msg).get()
        kafkaTemplate.send(KafkaTopics.ORDER_EVENTS, msg.orderId.toString(), msg).get()

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(salesCountOf(30L)).isEqualTo(5)
        }
    }
}
