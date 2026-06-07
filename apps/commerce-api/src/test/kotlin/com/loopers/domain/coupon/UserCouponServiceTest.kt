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
        userCouponService = UserCouponService(userCouponRepositoryPort)
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
}
