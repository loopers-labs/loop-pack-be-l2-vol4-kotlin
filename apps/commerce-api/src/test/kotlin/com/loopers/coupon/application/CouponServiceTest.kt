package com.loopers.coupon.application

import com.loopers.coupon.domain.Coupon
import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponRepository
import com.loopers.coupon.domain.CouponType
import com.loopers.support.error.BadRequestException
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CouponServiceTest {
    private val couponRepository: CouponRepository = mock()
    private val service = CouponService(couponRepository = couponRepository)

    @DisplayName("만료일이 현재보다 과거이면 BAD_REQUEST 예외가 발생하고 저장하지 않는다.")
    @Test
    fun throwsBadRequestAndDoesNotSave_whenExpiredAtIsInPast() {
        val command = createCommand(expiredAt = LocalDateTime.now().minusDays(1))

        val result = assertThrows<BadRequestException> {
            service.create(command)
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(CouponErrorCode.EXPIRED_AT_IN_PAST) },
            { verify(couponRepository, never()).save(any()) },
        )
    }

    @DisplayName("유효한 발행 요청이면 커맨드 값으로 쿠폰을 저장한다.")
    @Test
    fun savesCoupon_whenCommandIsValid() {
        val command = createCommand(
            couponType = CouponType.FIXED,
            value = 2000,
            minOrderAmount = 10000,
            requestAccountId = 7L,
        )
        whenever(couponRepository.save(any())).thenAnswer { it.arguments[0] as Coupon }

        service.create(command)

        val captor = argumentCaptor<Coupon>()
        verify(couponRepository).save(captor.capture())
        val saved = captor.firstValue
        assertAll(
            { assertThat(saved.type).isEqualTo(CouponType.FIXED) },
            { assertThat(saved.value).isEqualTo(2000) },
            { assertThat(saved.minOrderAmount.amount).isEqualTo(10000) },
            { assertThat(saved.createdBy).isEqualTo(7L) },
        )
    }

    private fun createCommand(
        couponName: String = "테스트쿠폰",
        expiredAt: LocalDateTime = LocalDateTime.now().plusDays(1),
        couponType: CouponType = CouponType.FIXED,
        value: Long = 1000,
        minOrderAmount: Long = 0,
        requestAccountId: Long = 1L,
    ): CouponCreateCommand = CouponCreateCommand(
        couponName = couponName,
        expiredAt = expiredAt,
        couponType = couponType,
        value = value,
        minOrderAmount = minOrderAmount,
        requestAccountId = requestAccountId,
    )
}
