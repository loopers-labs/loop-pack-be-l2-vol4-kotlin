package com.loopers.domain.coupon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CouponIssueRequestTest {

    @DisplayName("CouponIssueRequest.pending()")
    @Nested
    inner class Pending {
        @DisplayName("유효한 값으로 PENDING 발급 요청을 생성한다.")
        @Test
        fun createsPendingRequest() {
            val request = CouponIssueRequest.pending(userId = 1L, couponTemplateId = 2L, idempotencyKey = "key-123")

            assertThat(request.status).isEqualTo(CouponIssueStatus.PENDING)
            assertThat(request.failureReason).isNull()
            assertThat(request.userId).isEqualTo(1L)
            assertThat(request.couponTemplateId).isEqualTo(2L)
        }

        @DisplayName("userId가 0 이하면 예외를 발생시킨다.")
        @Test
        fun throwsWhenUserIdInvalid() {
            assertThrows<IllegalArgumentException> {
                CouponIssueRequest.pending(userId = 0L, couponTemplateId = 1L, idempotencyKey = "k")
            }
        }

        @DisplayName("couponTemplateId가 0 이하면 예외를 발생시킨다.")
        @Test
        fun throwsWhenCouponTemplateIdInvalid() {
            assertThrows<IllegalArgumentException> {
                CouponIssueRequest.pending(userId = 1L, couponTemplateId = 0L, idempotencyKey = "k")
            }
        }

        @DisplayName("idempotencyKey가 빈 문자열이면 예외를 발생시킨다.")
        @Test
        fun throwsWhenIdempotencyKeyBlank() {
            assertThrows<IllegalArgumentException> {
                CouponIssueRequest.pending(userId = 1L, couponTemplateId = 1L, idempotencyKey = "   ")
            }
        }
    }

    @DisplayName("complete()")
    @Nested
    inner class Complete {
        @DisplayName("PENDING 상태에서 complete()를 호출하면 COMPLETED 상태가 된다.")
        @Test
        fun transitionsToCompleted() {
            val request = CouponIssueRequest.pending(userId = 1L, couponTemplateId = 2L, idempotencyKey = "k")

            val completed = request.complete()

            assertThat(completed.status).isEqualTo(CouponIssueStatus.COMPLETED)
            assertThat(completed.failureReason).isNull()
        }
    }

    @DisplayName("fail()")
    @Nested
    inner class Fail {
        @DisplayName("DUPLICATE 사유로 fail()을 호출하면 FAILED 상태에 사유가 설정된다.")
        @Test
        fun transitionsToFailedWithDuplicate() {
            val request = CouponIssueRequest.pending(userId = 1L, couponTemplateId = 2L, idempotencyKey = "k")

            val failed = request.fail(CouponIssueFailureReason.DUPLICATE)

            assertThat(failed.status).isEqualTo(CouponIssueStatus.FAILED)
            assertThat(failed.failureReason).isEqualTo(CouponIssueFailureReason.DUPLICATE)
        }

        @DisplayName("SYSTEM_ERROR 사유로 fail()을 호출하면 FAILED 상태에 사유가 설정된다.")
        @Test
        fun transitionsToFailedWithSystemError() {
            val request = CouponIssueRequest.pending(userId = 1L, couponTemplateId = 2L, idempotencyKey = "k")

            val failed = request.fail(CouponIssueFailureReason.SYSTEM_ERROR)

            assertThat(failed.status).isEqualTo(CouponIssueStatus.FAILED)
            assertThat(failed.failureReason).isEqualTo(CouponIssueFailureReason.SYSTEM_ERROR)
        }
    }
}
