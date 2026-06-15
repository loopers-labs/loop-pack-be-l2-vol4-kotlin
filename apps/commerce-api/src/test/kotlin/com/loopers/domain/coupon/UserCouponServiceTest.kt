package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class UserCouponServiceTest {

    private lateinit var userCouponRepositoryPort: UserCouponRepositoryPort
    private lateinit var couponTemplateRepositoryPort: CouponTemplateRepositoryPort
    private lateinit var userCouponService: UserCouponService

    private val expiredAt = LocalDateTime.parse("2026-12-31T23:59:59")

    private fun template(id: Long = 1L, expiredAt: LocalDateTime = this.expiredAt) = CouponTemplate(
        id = id,
        name = "1만원 할인",
        type = CouponType.FIXED,
        value = 10_000L,
        minOrderAmount = 0L,
        expiredAt = expiredAt,
    )

    @BeforeEach
    fun setUp() {
        userCouponRepositoryPort = mockk()
        couponTemplateRepositoryPort = mockk()
        userCouponService = UserCouponService(userCouponRepositoryPort, couponTemplateRepositoryPort)
    }

    @DisplayName("발급 가능한 템플릿이면, AVAILABLE 상태의 발급 쿠폰을 저장하고 반환한다.")
    @Test
    fun issuesAvailableCoupon_whenValid() {
        val now = LocalDateTime.parse("2026-06-07T10:00:00")
        val saved = slot<UserCoupon>()
        every { userCouponRepositoryPort.existsByUserIdAndCouponTemplateId(9L, 1L) } returns false
        every { userCouponRepositoryPort.save(capture(saved)) } answers { saved.captured.copy(id = 100L) }

        val result = userCouponService.issue(template(), userId = 9L, now = now)

        assertThat(result.id).isEqualTo(100L)
        assertThat(result.couponTemplateId).isEqualTo(1L)
        assertThat(result.userId).isEqualTo(9L)
        assertThat(result.status).isEqualTo(CouponStatus.AVAILABLE)
        assertThat(result.issuedAt).isEqualTo(now)
        assertThat(result.usedAt).isNull()
        verify(exactly = 1) { userCouponRepositoryPort.save(any()) }
    }

    @DisplayName("이미 만료된 템플릿을 발급하려 하면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenTemplateExpired() {
        val now = LocalDateTime.parse("2027-01-01T00:00:00")

        val ex = assertThrows<CoreException> {
            userCouponService.issue(template(), userId = 9L, now = now)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        verify(exactly = 0) { userCouponRepositoryPort.save(any()) }
    }

    @DisplayName("동일 사용자가 동일 템플릿을 이미 보유하면(1인 1매), CONFLICT 예외가 발생한다.")
    @Test
    fun throwsConflict_whenAlreadyIssued() {
        val now = LocalDateTime.parse("2026-06-07T10:00:00")
        every { userCouponRepositoryPort.existsByUserIdAndCouponTemplateId(9L, 1L) } returns true

        val ex = assertThrows<CoreException> {
            userCouponService.issue(template(), userId = 9L, now = now)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
        verify(exactly = 0) { userCouponRepositoryPort.save(any()) }
    }

    private val now = LocalDateTime.parse("2026-06-07T10:00:00")

    private fun availableCoupon(
        id: Long = 50L,
        userId: Long = 9L,
        templateId: Long = 1L,
        status: CouponStatus = CouponStatus.AVAILABLE,
        usedAt: LocalDateTime? = null,
    ) = UserCoupon(
        id = id,
        couponTemplateId = templateId,
        userId = userId,
        status = status,
        issuedAt = LocalDateTime.parse("2026-06-01T10:00:00"),
        usedAt = usedAt,
    )

    @DisplayName("useForOrder 는, ")
    @org.junit.jupiter.api.Nested
    inner class UseForOrder {
        @DisplayName("정상 쿠폰이면 USED 로 전이해 저장하고 할인액이 담긴 CouponUsage 를 반환한다.")
        @Test
        fun succeeds_whenValid() {
            every { userCouponRepositoryPort.findById(50L) } returns availableCoupon()
            every { couponTemplateRepositoryPort.findById(1L) } returns template()
            val saved = slot<UserCoupon>()
            every { userCouponRepositoryPort.save(capture(saved)) } answers { saved.captured }

            val usage = userCouponService.useForOrder(couponId = 50L, userId = 9L, orderAmount = 30_000L, now = now)

            assertThat(usage.discount).isEqualTo(10_000L)
            assertThat(usage.usedCoupon.status).isEqualTo(CouponStatus.USED)
            assertThat(usage.usedCoupon.usedAt).isEqualTo(now)
            assertThat(usage.template.id).isEqualTo(1L)
        }

        @DisplayName("미존재 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            every { userCouponRepositoryPort.findById(50L) } returns null

            val ex = assertThrows<CoreException> {
                userCouponService.useForOrder(50L, 9L, 30_000L, now)
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
            verify(exactly = 0) { userCouponRepositoryPort.save(any()) }
        }

        @DisplayName("타 사용자 소유 쿠폰이면 FORBIDDEN 예외가 발생한다.")
        @Test
        fun throwsForbidden_whenOtherUser() {
            every { userCouponRepositoryPort.findById(50L) } returns availableCoupon(userId = 8L)

            val ex = assertThrows<CoreException> {
                userCouponService.useForOrder(50L, 9L, 30_000L, now)
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.FORBIDDEN)
            verify(exactly = 0) { userCouponRepositoryPort.save(any()) }
        }

        @DisplayName("AVAILABLE 이 아닌 쿠폰(USED)이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNotAvailable() {
            every { userCouponRepositoryPort.findById(50L) } returns
                availableCoupon(status = CouponStatus.USED, usedAt = now)

            val ex = assertThrows<CoreException> {
                userCouponService.useForOrder(50L, 9L, 30_000L, now)
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            verify(exactly = 0) { userCouponRepositoryPort.save(any()) }
        }

        @DisplayName("만료/최소 주문 금액 미달이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNotApplicable() {
            every { userCouponRepositoryPort.findById(50L) } returns availableCoupon()
            every { couponTemplateRepositoryPort.findById(1L) } returns
                template().copy(minOrderAmount = 50_000L)

            val ex = assertThrows<CoreException> {
                userCouponService.useForOrder(50L, 9L, 30_000L, now)
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            verify(exactly = 0) { userCouponRepositoryPort.save(any()) }
        }
    }

    @DisplayName("restore 는, USED 쿠폰을 AVAILABLE 로 되돌려 저장한다.")
    @Test
    fun restore_revertsToAvailable() {
        every { userCouponRepositoryPort.findById(50L) } returns
            availableCoupon(status = CouponStatus.USED, usedAt = now)
        val saved = slot<UserCoupon>()
        every { userCouponRepositoryPort.save(capture(saved)) } answers { saved.captured }

        val restored = userCouponService.restore(50L)

        assertThat(restored.status).isEqualTo(CouponStatus.AVAILABLE)
        assertThat(restored.usedAt).isNull()
    }
}
