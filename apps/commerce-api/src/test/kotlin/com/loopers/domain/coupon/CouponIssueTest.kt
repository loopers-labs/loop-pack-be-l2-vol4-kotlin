package com.loopers.domain.coupon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.ZonedDateTime

class CouponIssueTest {
    @DisplayName("CouponIssue 생성")
    @Nested
    inner class Create {
        @DisplayName("발급 시점의 쿠폰 조건 스냅샷을 저장한다")
        @Test
        fun createsCouponIssueWithSnapshot() {
            val expiredAt = ZonedDateTime.parse("2099-12-31T23:59:59+09:00")

            val issue = CouponIssue(
                memberId = 1L,
                couponId = 10L,
                status = CouponIssueStatus.AVAILABLE,
                type = DiscountType.RATE,
                discountValue = 10L,
                minOrderAmount = 10_000L,
                expiredAt = expiredAt,
                usedAt = null,
            )

            assertAll(
                { assertThat(issue.memberId).isEqualTo(1L) },
                { assertThat(issue.couponId).isEqualTo(10L) },
                { assertThat(issue.status).isEqualTo(CouponIssueStatus.AVAILABLE) },
                { assertThat(issue.type).isEqualTo(DiscountType.RATE) },
                { assertThat(issue.discountValue).isEqualTo(10L) },
                { assertThat(issue.minOrderAmount).isEqualTo(10_000L) },
                { assertThat(issue.expiredAt).isEqualTo(expiredAt) },
                { assertThat(issue.usedAt).isNull() },
            )
        }
    }

    @DisplayName("쿠폰 발급 상태 표시")
    @Nested
    inner class DisplayStatus {
        @DisplayName("사용 가능한 쿠폰의 만료일이 지나면 만료 상태로 표시한다")
        @Test
        fun returnsExpired_whenAvailableCouponIsExpired() {
            val issue = createCouponIssue(
                status = CouponIssueStatus.AVAILABLE,
                expiredAt = ZonedDateTime.parse("2026-01-01T00:00:00+09:00"),
            )

            val result = issue.displayStatusAt(ZonedDateTime.parse("2026-01-02T00:00:00+09:00"))

            assertThat(result).isEqualTo(CouponIssueDisplayStatus.EXPIRED)
        }

        @DisplayName("사용 완료된 쿠폰은 만료일이 지나도 사용 완료 상태로 표시한다")
        @Test
        fun returnsUsed_whenCouponIsUsed() {
            val issue = createCouponIssue(
                status = CouponIssueStatus.USED,
                expiredAt = ZonedDateTime.parse("2026-01-01T00:00:00+09:00"),
                usedAt = ZonedDateTime.parse("2025-12-31T00:00:00+09:00"),
            )

            val result = issue.displayStatusAt(ZonedDateTime.parse("2026-01-02T00:00:00+09:00"))

            assertThat(result).isEqualTo(CouponIssueDisplayStatus.USED)
        }

        @DisplayName("사용 가능하고 만료되지 않은 쿠폰은 사용 가능 상태로 표시한다")
        @Test
        fun returnsAvailable_whenCouponIsAvailableAndNotExpired() {
            val issue = createCouponIssue(
                status = CouponIssueStatus.AVAILABLE,
                expiredAt = ZonedDateTime.parse("2026-01-02T00:00:00+09:00"),
            )

            val result = issue.displayStatusAt(ZonedDateTime.parse("2026-01-01T00:00:00+09:00"))

            assertThat(result).isEqualTo(CouponIssueDisplayStatus.AVAILABLE)
        }
    }

    private fun createCouponIssue(
        status: CouponIssueStatus = CouponIssueStatus.AVAILABLE,
        expiredAt: ZonedDateTime = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
        usedAt: ZonedDateTime? = null,
    ): CouponIssue {
        return CouponIssue(
            memberId = 1L,
            couponId = 10L,
            status = status,
            type = DiscountType.RATE,
            discountValue = 10L,
            minOrderAmount = 10_000L,
            expiredAt = expiredAt,
            usedAt = usedAt,
        )
    }
}
