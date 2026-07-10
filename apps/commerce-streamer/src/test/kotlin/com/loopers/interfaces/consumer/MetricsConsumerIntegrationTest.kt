package com.loopers.interfaces.consumer

import com.loopers.domain.metrics.ProductMetricRepository
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

// auto.offset.reset=latest(kafka.yml)라 컨슈머 구독 전 발행분은 소실될 수 있어, 테스트에서만 earliest로 재정의한다.
@TestPropertySource(properties = ["spring.kafka.properties.auto.offset.reset=earliest"])
@SpringBootTest
class MetricsConsumerIntegrationTest {
    @Autowired lateinit var metricRepository: ProductMetricRepository

    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp

    @Value("\${spring.kafka.bootstrap-servers}")
    lateinit var bootstrap: String

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("catalog-events LIKE_ADDED 를 소비하면 product_metrics.like_count가 증가한다.")
    @Test
    fun consumesLikeAdded() {
        publish("catalog-events", "10", """{"eventId":"c1","type":"LIKE_ADDED","productId":10}""")
        val metric = awaitMetric(10L)
        assertThat(metric?.likeCount).isEqualTo(1L)
    }

    @DisplayName("같은 eventId를 두 번 발행해도 like_count는 1만 반영된다(멱등).")
    @Test
    fun idempotentAcrossDuplicateMessages() {
        publish("catalog-events", "20", """{"eventId":"c2","type":"LIKE_ADDED","productId":20}""")
        publish("catalog-events", "20", """{"eventId":"c2","type":"LIKE_ADDED","productId":20}""")
        awaitMetric(20L)
        Thread.sleep(3000)
        assertThat(metricRepository.findByProductId(20L)?.likeCount).isEqualTo(1L)
    }

    @DisplayName("잘못된 형식의 레코드가 있어도 같은 배치의 정상 레코드는 처리된다(레코드 단위 격리).")
    @Test
    fun isolatesMalformedRecordFromValidRecordInSameBatch() {
        publish("catalog-events", "30", "not-json")
        publish("catalog-events", "31", """{"eventId":"c3","type":"LIKE_ADDED","productId":31}""")
        val metric = awaitMetric(31L)
        assertThat(metric?.likeCount).isEqualTo(1L)
    }

    private fun awaitMetric(productId: Long) = run {
        var m = metricRepository.findByProductId(productId)
        var tries = 0
        while (m == null && tries < 50) {
            Thread.sleep(200)
            m = metricRepository.findByProductId(productId)
            tries++
        }
        m
    }

    private fun publish(topic: String, key: String, value: String) {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )
        KafkaProducer<String, String>(props).use { it.send(ProducerRecord(topic, key, value)).get() }
    }
}
