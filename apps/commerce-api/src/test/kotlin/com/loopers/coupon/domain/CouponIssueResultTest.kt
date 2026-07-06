package com.loopers.coupon.domain

import com.loopers.support.error.ConflictException
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows

class CouponIssueResultTest {
    @DisplayName("발급 확정하면 ISSUED 상태와 userCouponId, 확정 시각이 기록된다.")
    @Test
    fun marksIssued_withUserCouponIdAndDecidedAt() {
        val result = pendingResult()

        result.markIssued(userCouponId = 7L, decidedAt = DECIDED_AT)

        assertAll(
            { assertThat(result.status).isEqualTo(CouponIssueResultStatus.ISSUED) },
            { assertThat(result.userCouponId).isEqualTo(7L) },
            { assertThat(result.decidedAt).isEqualTo(DECIDED_AT) },
            { assertThat(result.rejectReason).isNull() },
        )
    }

    @DisplayName("발급 거절하면 REJECTED 상태와 거절 사유, 확정 시각이 기록된다.")
    @Test
    fun marksRejected_withReasonAndDecidedAt() {
        val result = pendingResult()

        result.markRejected(rejectReason = CouponErrorCode.SOLD_OUT.code, decidedAt = DECIDED_AT)

        assertAll(
            { assertThat(result.status).isEqualTo(CouponIssueResultStatus.REJECTED) },
            { assertThat(result.rejectReason).isEqualTo(CouponErrorCode.SOLD_OUT.code) },
            { assertThat(result.decidedAt).isEqualTo(DECIDED_AT) },
            { assertThat(result.userCouponId).isNull() },
        )
    }

    @DisplayName("이미 확정된 결과를 다시 확정하면 CONFLICT(ALREADY_DECIDED) 예외가 발생한다.")
    @Test
    fun throwsConflict_whenDecidingAlreadyDecidedResult() {
        val issued = pendingResult().apply { markIssued(7L, DECIDED_AT) }
        val rejected = pendingResult().apply { markRejected(CouponErrorCode.SOLD_OUT.code, DECIDED_AT) }

        listOf(
            { issued.markIssued(8L, DECIDED_AT) },
            { issued.markRejected(CouponErrorCode.SOLD_OUT.code, DECIDED_AT) },
            { rejected.markIssued(8L, DECIDED_AT) },
            { rejected.markRejected(CouponErrorCode.SOLD_OUT.code, DECIDED_AT) },
        ).forEach { decide ->
            val result = assertThrows<ConflictException> { decide() }
            assertThat(result.errorCode).isEqualTo(CouponErrorCode.ALREADY_DECIDED)
        }
    }

    private fun pendingResult(): CouponIssueResult = CouponIssueResult(
        requestId = "request-1",
        couponId = 1L,
        userId = 10L,
        requestedAt = LocalDateTime.of(2026, 7, 1, 10, 0),
    )

    private companion object {
        private val DECIDED_AT: LocalDateTime = LocalDateTime.of(2026, 7, 1, 10, 0, 3)
    }
}
