package com.loopers.coupon.application

import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponIssueResult
import com.loopers.coupon.domain.CouponIssueResultRepository
import com.loopers.coupon.domain.CouponIssueResultStatus
import com.loopers.coupon.domain.CouponRepository
import com.loopers.coupon.domain.CouponType
import com.loopers.coupon.infrastructure.UserCouponJpaRepository
import com.loopers.coupon.infrastructure.messaging.CouponIssueRequestConsumer
import com.loopers.coupon.infrastructure.messaging.CouponIssueRequestEvent
import com.loopers.shared.domain.Money
import com.loopers.support.DatabaseCleanup
import java.time.LocalDateTime
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.support.Acknowledgment
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueConsumerIntegrationTest @Autowired constructor(
    private val couponIssueRequestConsumer: CouponIssueRequestConsumer,
    private val couponRepository: CouponRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val couponIssueResultRepository: CouponIssueResultRepository,
    private val databaseCleanup: DatabaseCleanup,
) {
    @BeforeEach
    fun setUp() {
        databaseCleanup.execute()
    }

    @DisplayName("발급 요청 이벤트를 소비하면 수량 증가·UserCoupon 저장·ISSUED 기록이 함께 확정되고 ack한다.")
    @Test
    fun issuesAndRecordsResult_whenEventConsumed() {
        val coupon = firstComeCoupon(totalQuantity = 10)
        val acknowledgment = mock<Acknowledgment>()
        val event = issueEvent(couponId = coupon.id, userId = 1L)

        couponIssueRequestConsumer.consume(event, acknowledgment)

        val result = couponIssueResultRepository.findById(event.requestId)!!
        val userCoupon = userCouponJpaRepository.findByUserIdAndCouponId(1L, coupon.id)!!
        assertAll(
            { assertThat(result.status).isEqualTo(CouponIssueResultStatus.ISSUED) },
            { assertThat(result.userCouponId).isEqualTo(userCoupon.id) },
            { assertThat(result.decidedAt).isNotNull() },
            { assertThat(couponRepository.findById(coupon.id)!!.issuedQuantity).isEqualTo(1) },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("매진된 쿠폰의 발급 요청은 REJECTED(SOLD_OUT)로 확정되고 수량이 늘지 않는다.")
    @Test
    fun rejectsWithSoldOut_whenCouponExhausted() {
        val coupon = firstComeCoupon(totalQuantity = 1)
        couponIssueRequestConsumer.consume(issueEvent(coupon.id, userId = 1L), mock())

        val event = issueEvent(coupon.id, userId = 2L)
        couponIssueRequestConsumer.consume(event, mock())

        val result = couponIssueResultRepository.findById(event.requestId)!!
        assertAll(
            { assertThat(result.status).isEqualTo(CouponIssueResultStatus.REJECTED) },
            { assertThat(result.rejectReason).isEqualTo(CouponErrorCode.SOLD_OUT.code) },
            { assertThat(couponRepository.findById(coupon.id)!!.issuedQuantity).isEqualTo(1) },
            { assertThat(userCouponJpaRepository.countByCouponId(coupon.id)).isEqualTo(1) },
        )
    }

    @DisplayName("이미 발급받은 사용자의 새 요청은 REJECTED(ALREADY_ISSUED)로 확정된다.")
    @Test
    fun rejectsWithAlreadyIssued_whenUserAlreadyHasCoupon() {
        val coupon = firstComeCoupon(totalQuantity = 10)
        couponIssueRequestConsumer.consume(issueEvent(coupon.id, userId = 1L), mock())

        val event = issueEvent(coupon.id, userId = 1L)
        couponIssueRequestConsumer.consume(event, mock())

        val result = couponIssueResultRepository.findById(event.requestId)!!
        assertAll(
            { assertThat(result.status).isEqualTo(CouponIssueResultStatus.REJECTED) },
            { assertThat(result.rejectReason).isEqualTo(CouponErrorCode.ALREADY_ISSUED.code) },
            { assertThat(couponRepository.findById(coupon.id)!!.issuedQuantity).isEqualTo(1) },
            { assertThat(userCouponJpaRepository.countByCouponId(coupon.id)).isEqualTo(1) },
        )
    }

    @DisplayName("같은 requestId가 재전송되어도 중복 차감 없이 기존 확정이 유지되고 ack한다. (멱등)")
    @Test
    fun skipsRedelivery_whenRequestAlreadyDecided() {
        val coupon = firstComeCoupon(totalQuantity = 10)
        val acknowledgment = mock<Acknowledgment>()
        val event = issueEvent(coupon.id, userId = 1L)
        couponIssueRequestConsumer.consume(event, acknowledgment)

        couponIssueRequestConsumer.consume(event, acknowledgment)

        val result = couponIssueResultRepository.findById(event.requestId)!!
        assertAll(
            { assertThat(result.status).isEqualTo(CouponIssueResultStatus.ISSUED) },
            { assertThat(couponRepository.findById(coupon.id)!!.issuedQuantity).isEqualTo(1) },
            { assertThat(userCouponJpaRepository.countByCouponId(coupon.id)).isEqualTo(1) },
            { verify(acknowledgment, times(2)).acknowledge() },
        )
    }

    @DisplayName("PENDING으로 남은 요청이 재전송되면 이어서 처리해 확정한다. (crash 복구)")
    @Test
    fun resumesProcessing_whenPendingResultRedelivered() {
        val coupon = firstComeCoupon(totalQuantity = 10)
        val event = issueEvent(coupon.id, userId = 1L)
        couponIssueRequestConsumer.consume(event, mock())
        val pendingEvent = issueEvent(coupon.id, userId = 2L)
        registerPendingOnly(pendingEvent)

        couponIssueRequestConsumer.consume(pendingEvent, mock())

        val result = couponIssueResultRepository.findById(pendingEvent.requestId)!!
        assertAll(
            { assertThat(result.status).isEqualTo(CouponIssueResultStatus.ISSUED) },
            { assertThat(couponRepository.findById(coupon.id)!!.issuedQuantity).isEqualTo(2) },
        )
    }

    @DisplayName("존재하지 않는 쿠폰의 발급 요청은 REJECTED(COUPON_NOT_FOUND)로 확정된다.")
    @Test
    fun rejectsWithNotFound_whenCouponDoesNotExist() {
        val event = issueEvent(couponId = 999_999L, userId = 1L)

        couponIssueRequestConsumer.consume(event, mock())

        val result = couponIssueResultRepository.findById(event.requestId)!!
        assertAll(
            { assertThat(result.status).isEqualTo(CouponIssueResultStatus.REJECTED) },
            { assertThat(result.rejectReason).isEqualTo(CouponErrorCode.COUPON_NOT_FOUND.code) },
        )
    }

    private fun registerPendingOnly(event: CouponIssueRequestEvent) {
        couponIssueResultRepository.save(
            CouponIssueResult(
                requestId = event.requestId,
                couponId = event.couponId,
                userId = event.userId,
                requestedAt = event.requestedAt,
            ),
        )
    }

    private fun issueEvent(couponId: Long, userId: Long): CouponIssueRequestEvent = CouponIssueRequestEvent(
        requestId = UUID.randomUUID().toString(),
        couponId = couponId,
        userId = userId,
        requestedAt = LocalDateTime.now(),
    )

    private fun firstComeCoupon(totalQuantity: Long): Coupon = couponRepository.save(
        Coupon(
            type = CouponType.FIXED,
            name = "선착순쿠폰",
            value = 1000,
            minOrderAmount = Money(0),
            expiredAt = LocalDateTime.now().plusDays(1),
            createdBy = 1L,
            totalQuantity = totalQuantity,
        ),
    )
}
