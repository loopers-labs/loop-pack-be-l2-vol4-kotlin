package com.loopers.application.coupon

import com.loopers.domain.coupon.enums.CouponIssueRequestStatus
import com.loopers.domain.coupon.enums.DiscountType
import com.loopers.event.CouponIssueRequestMessage
import com.loopers.infrastructure.coupon.entity.CouponEntity
import com.loopers.infrastructure.coupon.entity.CouponIssueRequestEntity
import com.loopers.infrastructure.coupon.repository.CouponIssueJpaRepository
import com.loopers.infrastructure.coupon.repository.CouponIssueRequestJpaRepository
import com.loopers.infrastructure.coupon.repository.CouponJpaRepository
import com.loopers.infrastructure.event.repository.EventHandledJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class CouponIssueRequestProcessorIntegrationTest @Autowired constructor(
    private val processor: CouponIssueRequestProcessor,
    private val couponJpaRepository: CouponJpaRepository,
    private val couponIssueJpaRepository: CouponIssueJpaRepository,
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("쿠폰 발급 요청 이벤트를 처리하면 쿠폰을 발급하고 요청을 완료한다")
    @Test
    fun issuesCoupon() {
        val coupon = couponJpaRepository.save(createCouponEntity(issueLimit = 1L))
        val request = couponIssueRequestJpaRepository.save(
            createRequestEntity(requestId = "request-1", couponId = coupon.id, memberId = 1L),
        )
        val message = createMessage(requestId = request.requestId, couponId = coupon.id, memberId = 1L)

        processor.handle(message)
        processor.handle(message)

        val issues = couponIssueJpaRepository.findAll()
        val handled = eventHandledJpaRepository.findByConsumerGroupAndEventId(
            consumerGroup = "commerce-coupon-issue",
            eventId = message.eventId,
        )
        val processedRequest = couponIssueRequestJpaRepository.findByRequestId(request.requestId)
        val processedCoupon = couponJpaRepository.findById(coupon.id).orElseThrow()
        assertAll(
            { assertThat(issues).hasSize(1) },
            { assertThat(issues.single().couponId).isEqualTo(coupon.id) },
            { assertThat(issues.single().memberId).isEqualTo(1L) },
            { assertThat(processedCoupon.issuedCount).isEqualTo(1L) },
            { assertThat(processedRequest?.status).isEqualTo(CouponIssueRequestStatus.ISSUED) },
            { assertThat(processedRequest?.issueId).isEqualTo(issues.single().id) },
            { assertThat(handled?.consumerGroup).isEqualTo("commerce-coupon-issue") },
            { assertThat(handled?.eventId).isEqualTo(message.eventId) },
        )
    }

    @DisplayName("동시에 발급 요청을 처리해도 발급 수량 제한을 넘지 않는다")
    @Test
    fun doesNotExceedIssueLimit_whenRequestsAreConcurrent() {
        val issueLimit = 3L
        val coupon = couponJpaRepository.save(createCouponEntity(issueLimit = issueLimit))
        val messages = (1L..10L).map { memberId ->
            val request = couponIssueRequestJpaRepository.save(
                createRequestEntity(
                    requestId = "request-$memberId",
                    couponId = coupon.id,
                    memberId = memberId,
                ),
            )
            createMessage(
                eventId = "event-$memberId",
                requestId = request.requestId,
                couponId = coupon.id,
                memberId = memberId,
            )
        }
        val executor = Executors.newFixedThreadPool(messages.size)
        val ready = CountDownLatch(messages.size)
        val start = CountDownLatch(1)

        val futures = messages.map { message ->
            executor.submit {
                ready.countDown()
                start.await(5, TimeUnit.SECONDS)
                processor.handle(message)
            }
        }
        ready.await(5, TimeUnit.SECONDS)
        start.countDown()
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        executor.shutdown()

        val requests = couponIssueRequestJpaRepository.findAll()
        val processedCoupon = couponJpaRepository.findById(coupon.id).orElseThrow()
        assertAll(
            { assertThat(couponIssueJpaRepository.countByCouponId(coupon.id)).isEqualTo(issueLimit) },
            { assertThat(processedCoupon.issuedCount).isEqualTo(issueLimit) },
            { assertThat(requests.count { it.status == CouponIssueRequestStatus.ISSUED }).isEqualTo(issueLimit.toInt()) },
            {
                assertThat(requests.count { it.status == CouponIssueRequestStatus.REJECTED })
                    .isEqualTo(messages.size - issueLimit.toInt())
            },
        )
    }

    private fun createCouponEntity(
        issueLimit: Long?,
        expiredAt: ZonedDateTime = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
        isDeleted: Boolean = false,
    ): CouponEntity {
        return CouponEntity(
            name = "선착순 쿠폰",
            type = DiscountType.FIXED,
            discountValue = 3_000L,
            minOrderAmount = 10_000L,
            expiredAt = expiredAt,
            isDeleted = isDeleted,
            issueLimit = issueLimit,
        )
    }

    private fun createRequestEntity(
        requestId: String,
        couponId: Long,
        memberId: Long,
    ): CouponIssueRequestEntity {
        return CouponIssueRequestEntity(
            requestId = requestId,
            couponId = couponId,
            memberId = memberId,
            status = CouponIssueRequestStatus.REQUESTED,
            issueId = null,
            reason = null,
            requestedAt = ZonedDateTime.parse("2026-01-01T00:00:00+09:00"),
        )
    }

    private fun createMessage(
        requestId: String,
        couponId: Long,
        memberId: Long,
        eventId: String = "event-$requestId",
    ): CouponIssueRequestMessage {
        return CouponIssueRequestMessage(
            eventId = eventId,
            requestId = requestId,
            couponId = couponId,
            memberId = memberId,
            requestedAt = ZonedDateTime.parse("2026-01-01T00:00:00+09:00"),
        )
    }
}
