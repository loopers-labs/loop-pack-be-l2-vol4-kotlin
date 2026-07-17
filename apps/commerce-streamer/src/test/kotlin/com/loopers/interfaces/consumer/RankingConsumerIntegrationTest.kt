package com.loopers.interfaces.consumer

import com.loopers.domain.ranking.RankingKeyResolver
import com.loopers.utils.RedisCleanUp
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.context.TestPropertySource
import java.time.ZonedDateTime
import java.util.Properties
import java.util.UUID
import kotlin.math.log10

@SpringBootTest
@TestPropertySource(properties = ["spring.kafka.properties.auto.offset.reset=earliest"])
class RankingConsumerIntegrationTest @Autowired constructor(
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    private val keyResolver = RankingKeyResolver()

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    private fun publish(topic: String, key: String, value: String) {
        val props = Properties().apply {
            put("bootstrap.servers", bootstrapServers)
            put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
            put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
        }
        KafkaProducer<String, String>(props).use { it.send(ProducerRecord(topic, key, value)).get() }
    }

    // 기대 점수에 수렴할 때까지 폴링 — 배치가 나뉘어 소비돼 점수가 부분 반영된 시점의 단언 플레이크 방지
    private fun awaitScore(key: String, member: String, expected: Double): Double? {
        var last: Double? = null
        repeat(50) {
            last = redisTemplate.opsForZSet().score(key, member)
            if (last != null && kotlin.math.abs(last!! - expected) < 1e-6) return last
            Thread.sleep(200)
        }
        return last
    }

    @DisplayName("조회/좋아요/주문 이벤트가 일간·시간 ZSET에 가중치 점수로 반영된다.")
    @Test
    fun consumesEventsIntoRankingZSets() {
        val window = keyResolver.windowFor(ZonedDateTime.now())
        val productId = 910L

        publish("catalog-events", "$productId", """{"eventId":"${UUID.randomUUID()}","type":"PRODUCT_VIEWED","productId":$productId}""")
        publish("catalog-events", "$productId", """{"eventId":"${UUID.randomUUID()}","type":"LIKE_ADDED","productId":$productId}""")

        assertThat(awaitScore(window.dailyKey, "$productId", expected = 0.3))
            .isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-6))
        assertThat(redisTemplate.opsForZSet().score(window.hourlyKey, "$productId"))
            .isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-6))
    }

    @DisplayName("같은 eventId를 두 번 발행해도 점수는 한 번만 반영된다 — 멱등.")
    @Test
    fun ignoresDuplicateEventId() {
        val window = keyResolver.windowFor(ZonedDateTime.now())
        val productId = 920L
        val eventId = UUID.randomUUID().toString()
        val payload = """{"eventId":"$eventId","type":"LIKE_ADDED","productId":$productId}"""

        publish("catalog-events", "$productId", payload)
        publish("catalog-events", "$productId", payload)

        assertThat(awaitScore(window.dailyKey, "$productId", expected = 0.2))
            .isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-6))
        Thread.sleep(3000)
        assertThat(redisTemplate.opsForZSet().score(window.dailyKey, "$productId"))
            .isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-6))
    }

    @DisplayName("주문 이벤트는 0.6×log10(1+단가×수량) 점수로 반영된다 — 주문 1건 > 좋아요 3건.")
    @Test
    fun orderEventOutweighsLikes() {
        val window = keyResolver.windowFor(ZonedDateTime.now())
        val orderedProduct = 930L
        val likedProduct = 931L

        publish(
            "order-events",
            "1",
            """{"eventId":"${UUID.randomUUID()}","type":"PAYMENT_SUCCEEDED","orderId":1,"userId":2,
               "items":[{"productId":$orderedProduct,"quantity":1,"unitPrice":30000.00}]}""",
        )
        repeat(3) {
            publish("catalog-events", "$likedProduct", """{"eventId":"${UUID.randomUUID()}","type":"LIKE_ADDED","productId":$likedProduct}""")
        }

        val expectedOrderScore = 0.6 * log10(1.0 + 30000.0)
        val orderScore = awaitScore(window.dailyKey, "$orderedProduct", expected = expectedOrderScore)!!
        val likeScore = awaitScore(window.dailyKey, "$likedProduct", expected = 0.6)!!
        assertThat(likeScore).isCloseTo(0.6, org.assertj.core.data.Offset.offset(1e-6))
        assertThat(orderScore).isGreaterThan(likeScore)
    }
}
