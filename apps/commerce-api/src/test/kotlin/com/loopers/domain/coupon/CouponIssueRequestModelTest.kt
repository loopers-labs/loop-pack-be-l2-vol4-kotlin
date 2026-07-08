package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CouponIssueRequestModelTest {
    private fun request(): CouponIssueRequestModel =
        CouponIssueRequestModel(requestId = "req-1", userId = 1L, couponId = 10L)

    @DisplayName("발급 요청 상태를 전이할 때,")
    @Nested
    inner class Transition {
        @DisplayName("PENDING 이면 markSuccess 로 SUCCESS 가 되고 사유가 비워진다.")
        @Test
        fun marksSuccess_whenPending() {
            // arrange
            val request = request()

            // act
            request.markSuccess()

            // assert
            assertThat(request.status).isEqualTo(CouponIssueStatus.SUCCESS)
            assertThat(request.reason).isNull()
        }

        @DisplayName("PENDING 이면 markFailed 로 FAILED 가 되고 사유가 남는다.")
        @Test
        fun marksFailed_whenPending() {
            // arrange
            val request = request()

            // act
            request.markFailed("SOLD_OUT")

            // assert
            assertThat(request.status).isEqualTo(CouponIssueStatus.FAILED)
            assertThat(request.reason).isEqualTo("SOLD_OUT")
        }

        @DisplayName("이미 종결된 요청에 markSuccess 를 다시 호출하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenMarkSuccessOnFinalized() {
            // arrange: PENDING -> FAILED 로 종결
            val request = request()
            request.markFailed("SOLD_OUT")

            // act
            val result = assertThrows<CoreException> { request.markSuccess() }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("이미 종결된 요청에 markFailed 를 다시 호출하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenMarkFailedOnFinalized() {
            // arrange: PENDING -> SUCCESS 로 종결
            val request = request()
            request.markSuccess()

            // act
            val result = assertThrows<CoreException> { request.markFailed("AGAIN") }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }
}
