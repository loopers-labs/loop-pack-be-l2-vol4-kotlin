package com.loopers.interfaces.consumer

import com.loopers.config.kafka.KafkaTopics
import com.loopers.infrastructure.metrics.ProductMetricRepository
import com.loopers.interfaces.consumer.message.LikeChangedMessage
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

@SpringBootTest(properties = ["spring.kafka.properties.auto.offset.reset=earliest"])
@EmbeddedKafka(
    partitions = 3,
    topics = [KafkaTopics.CATALOG_EVENTS],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class CatalogEventConsumerIntegrationTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val productMetricRepository: ProductMetricRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    private fun likeCountOf(productId: Long): Int? =
        productMetricRepository.findById(productId).orElse(null)?.likeCount

    @DisplayName("LIKED 메시지를 소비하면 product_metrics like_count 가 1 증가한다")
    @Test
    fun likeIncrementsMetrics() {
        val productId = 100L
        kafkaTemplate.send(
            KafkaTopics.CATALOG_EVENTS,
            productId.toString(),
            LikeChangedMessage("evt-1", 1L, productId, LikeChangedMessage.LikedType.LIKED, ZonedDateTime.now()),
        ).get()

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(likeCountOf(productId)).isEqualTo(1)
        }
    }

    @DisplayName("같은 eventId 메시지를 두 번 보내도 멱등하게 1 만 반영된다 (event_handled)")
    @Test
    fun idempotentOnDuplicate() {
        val productId = 200L
        val msg = LikeChangedMessage("evt-dup", 1L, productId, LikeChangedMessage.LikedType.LIKED, ZonedDateTime.now())
        kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, productId.toString(), msg).get()
        kafkaTemplate.send(KafkaTopics.CATALOG_EVENTS, productId.toString(), msg).get()

        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(likeCountOf(productId)).isEqualTo(1)
        }
    }
}
