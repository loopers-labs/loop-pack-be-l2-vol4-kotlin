package com.loopers.concurrency

import com.loopers.application.coupon.CouponIssueRequestPayload
import com.loopers.config.kafka.KafkaTopics
import com.loopers.domain.coupon.CouponIssueRequestModel
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponService
import com.loopers.fixture.CouponModelFixture
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
import java.util.UUID

@SpringBootTest(properties = ["spring.kafka.properties.auto.offset.reset=earliest"])
@EmbeddedKafka(
    partitions = 3,
    topics = [KafkaTopics.COUPON_ISSUE_REQUESTS],
    bootstrapServersProperty = "spring.kafka.bootstrap-servers",
)
class CouponIssueConcurrencyTest @Autowired constructor(
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val couponRepository: CouponRepository,
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val userCouponService: UserCouponService,
    private val databaseCleanUp: DatabaseCleanUp,
){
    @AfterEach
    fun tearDown() = databaseCleanUp.truncateAllTables()

    @DisplayName("한 유저가 5번 요청해도 쿠폰은 1개만 발급된다")
    @Test
    fun requestUserFiveTimes() {
        // given
        val coupon = couponRepository.save(CouponModelFixture.defaults().toModel())
        val userId = 1L
        val requestIds = List(5) { UUID.randomUUID().toString() }
        requestIds.forEach { requestId ->
            couponIssueRequestRepository.save(CouponIssueRequestModel.of(requestId, coupon.id, userId))
        }

        // when
        requestIds.forEach { requestId ->
            kafkaTemplate.send(KafkaTopics.COUPON_ISSUE_REQUESTS, coupon.id.toString(),
                CouponIssueRequestPayload(requestId, coupon.id, userId))
        }

        // then
        await().atMost(Duration.ofSeconds(15)).untilAsserted {
            assertThat(userCouponService.countIssuedByCouponId(coupon.id)).isEqualTo(1)
        }
    }

    @DisplayName("105명이 동시에 발급 요청하면 정확히 100개만 발급되고 나머지는 SOLD_OUT")
    @Test
    fun firstComServed() {
        // given
        val coupon = couponRepository.save(CouponModelFixture.defaults().toModel())
        val requests = (1..105).map { userId ->
            val reqId = UUID.randomUUID().toString()
            couponIssueRequestRepository.save(CouponIssueRequestModel.of(reqId, coupon.id, userId.toLong()))
            reqId to userId.toLong()
        }

        // when
        runConcurrently(requests.size) { i ->
            val (reqId, userId) = requests[i]
            kafkaTemplate.send(KafkaTopics.COUPON_ISSUE_REQUESTS, coupon.id.toString(),
                CouponIssueRequestPayload(reqId, coupon.id, userId)).get()
        }

        // then
        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            assertThat(userCouponService.countIssuedByCouponId(coupon.id)).isEqualTo(100)

            val statuses = requests.map { (reqId, _) -> couponIssueRequestRepository.findByRequestId(reqId)!!.status }
            assertThat(statuses.count { it == CouponIssueStatus.SUCCESS }).isEqualTo(100)
            assertThat(statuses.count { it == CouponIssueStatus.SOLD_OUT }).isEqualTo(5)
        }
    }
}
