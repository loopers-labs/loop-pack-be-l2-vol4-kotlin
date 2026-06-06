package com.loopers.domain.coupon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.LocalDateTime

class UserCouponTest {
    private val issuedAt = LocalDateTime.parse("2026-06-01T12:00:00")
    private val now = LocalDateTime.parse("2026-06-02T12:00:00")

    private fun userCoupon(
        couponTemplateId: Long = 1L,
        userId: Long = 1L,
        status: CouponStatus = CouponStatus.AVAILABLE,
        usedAt: LocalDateTime? = null,
    ): UserCoupon = UserCoupon(
        couponTemplateId = couponTemplateId,
        userId = userId,
        status = status,
        issuedAt = issuedAt,
        usedAt = usedAt,
    )

    @DisplayName("issue / 생성 불변식")
    @Nested
    inner class Issue {
        @DisplayName("issue 는 AVAILABLE 상태, usedAt=null 로 발급된다.")
        @Test
        fun issuesAvailable() {
            val coupon = UserCoupon.issue(couponTemplateId = 5L, userId = 7L, issuedAt = issuedAt)

            assertThat(coupon.status).isEqualTo(CouponStatus.AVAILABLE)
            assertThat(coupon.usedAt).isNull()
            assertThat(coupon.couponTemplateId).isEqualTo(5L)
            assertThat(coupon.userId).isEqualTo(7L)
        }

        @DisplayName("couponTemplateId / userId 가 0 이하면 실패한다.")
        @Test
        fun rejectsNonPositiveIds() {
            assertThrows<IllegalArgumentException> { userCoupon(couponTemplateId = 0L) }
            assertThrows<IllegalArgumentException> { userCoupon(userId = 0L) }
        }

        @DisplayName("USED 가 아닌데 usedAt 이 있거나, USED 인데 usedAt 이 없으면 실패한다.")
        @Test
        fun rejectsInconsistentUsedAt() {
            assertThrows<IllegalArgumentException> {
                userCoupon(status = CouponStatus.AVAILABLE, usedAt = now)
            }
            assertThrows<IllegalArgumentException> {
                userCoupon(status = CouponStatus.USED, usedAt = null)
            }
        }
    }

    @DisplayName("use(now)")
    @Nested
    inner class Use {
        @DisplayName("AVAILABLE 에서 USED 로 전이하고 usedAt 을 기록한다.")
        @Test
        fun usesFromAvailable() {
            val coupon = userCoupon(status = CouponStatus.AVAILABLE)

            val used = coupon.use(now)

            assertThat(used.status).isEqualTo(CouponStatus.USED)
            assertThat(used.usedAt).isEqualTo(now)
            assertThat(coupon.status).isEqualTo(CouponStatus.AVAILABLE)
        }

        @DisplayName("USED / EXPIRED 상태에서는 예외를 던진다 (최대 1회 사용).")
        @ParameterizedTest
        @EnumSource(value = CouponStatus::class, names = ["USED", "EXPIRED"])
        fun rejectsWhenNotAvailable(status: CouponStatus) {
            val usedAt = if (status == CouponStatus.USED) now else null
            val coupon = userCoupon(status = status, usedAt = usedAt)

            assertThrows<IllegalArgumentException> { coupon.use(now) }
        }
    }

    @DisplayName("expireIfExpired(expiredAt, now)")
    @Nested
    inner class ExpireIfExpired {
        @DisplayName("AVAILABLE 이고 만료 시각을 지났으면 EXPIRED 로 전이한다.")
        @Test
        fun expiresWhenPastExpiry() {
            val coupon = userCoupon(status = CouponStatus.AVAILABLE)
            val expiredAt = now.minusSeconds(1)

            val result = coupon.expireIfExpired(expiredAt, now)

            assertThat(result.status).isEqualTo(CouponStatus.EXPIRED)
        }

        @DisplayName("AVAILABLE 이지만 아직 만료 전이면 그대로 AVAILABLE 을 유지한다.")
        @Test
        fun keepsAvailableWhenNotExpired() {
            val coupon = userCoupon(status = CouponStatus.AVAILABLE)
            val expiredAt = now.plusSeconds(1)

            val result = coupon.expireIfExpired(expiredAt, now)

            assertThat(result.status).isEqualTo(CouponStatus.AVAILABLE)
        }

        @DisplayName("AVAILABLE 이 아닌 상태(USED/EXPIRED)는 만료 시각과 무관하게 그대로 반환한다(멱등).")
        @ParameterizedTest
        @EnumSource(value = CouponStatus::class, names = ["USED", "EXPIRED"])
        fun keepsNonAvailableUnchanged(status: CouponStatus) {
            val usedAt = if (status == CouponStatus.USED) now else null
            val coupon = userCoupon(status = status, usedAt = usedAt)
            val expiredAt = now.minusSeconds(1)

            val result = coupon.expireIfExpired(expiredAt, now)

            assertThat(result.status).isEqualTo(status)
        }
    }

    @DisplayName("belongsTo(userId)")
    @Nested
    inner class BelongsTo {
        @DisplayName("소유자면 true, 아니면 false 를 반환한다.")
        @Test
        fun checksOwnership() {
            val coupon = userCoupon(userId = 1L)

            assertThat(coupon.belongsTo(1L)).isTrue()
            assertThat(coupon.belongsTo(2L)).isFalse()
        }
    }
}
