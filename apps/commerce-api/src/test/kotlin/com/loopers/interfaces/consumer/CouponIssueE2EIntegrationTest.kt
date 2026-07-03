package com.loopers.interfaces.consumer

import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.outbox.KafkaTopics
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
import java.math.BigDecimal
import java.time.ZonedDateTime

@TestPropertySource(properties = ["spring.kafka.properties.auto.offset.reset=earliest"])
@SpringBootTest
class CouponIssueE2EIntegrationTest {
    @Autowired lateinit var couponRepository: CouponRepository

    @Autowired lateinit var requestRepository: CouponIssueRequestRepository

    @Autowired lateinit var databaseCleanUp: DatabaseCleanUp

    @Value("\${spring.kafka.bootstrap-servers}")
    lateinit var bootstrap: String

    @AfterEach fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("coupon-issue-requests 를 소비하면 요청이 ISSUED 로 종결된다.")
    @Test
    fun consumesAndIssues() {
        val coupon = couponRepository.save(
            CouponModel(
                name = "선착순",
                type = CouponType.FIXED,
                discountValue = BigDecimal("1000"),
                minOrderAmount = null,
                expiredAt = ZonedDateTime.now().plusDays(1),
                totalQuantity = 10,
            ),
        )
        requestRepository.save(CouponIssueRequest(requestId = "req-e2e", userId = 1L, couponId = coupon.id))
        publish(
            coupon.id.toString(),
            """{"eventId":"ev1","type":"COUPON_ISSUE_REQUESTED","requestId":"req-e2e","userId":1,"couponId":${coupon.id}}""",
        )

        var status: CouponIssueStatus? = null
        var tries = 0
        while (status != CouponIssueStatus.ISSUED && tries < 50) {
            Thread.sleep(200)
            status = requestRepository.findByRequestId("req-e2e")?.status
            tries++
        }
        assertThat(status).isEqualTo(CouponIssueStatus.ISSUED)
    }

    private fun publish(key: String, value: String) {
        val props = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrap,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )
        KafkaProducer<String, String>(props).use { it.send(ProducerRecord(KafkaTopics.COUPON_ISSUE_REQUESTS, key, value)).get() }
    }
}
