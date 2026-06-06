package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.CouponType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class CouponApplicationServiceAdapterTest {

    private lateinit var couponTemplateService: CouponTemplateService
    private lateinit var couponApplicationService: CouponApplicationServiceAdapter

    private val expiredAt = LocalDateTime.parse("2026-12-31T23:59:59")

    @BeforeEach
    fun setUp() {
        couponTemplateService = mockk()
        couponApplicationService = CouponApplicationServiceAdapter(couponTemplateService)
    }

    @DisplayName("createCoupon은 command 값으로 도메인 서비스를 호출하고, 결과를 CouponResult로 매핑한다.")
    @Test
    fun delegatesToServiceAndMapsResult() {
        val command = CreateCouponCommand(
            name = "10% 할인",
            type = CouponType.RATE,
            value = 10L,
            minOrderAmount = 10_000L,
            expiredAt = expiredAt,
        )
        val created = CouponTemplate(
            id = 7L,
            name = "10% 할인",
            type = CouponType.RATE,
            value = 10L,
            minOrderAmount = 10_000L,
            expiredAt = expiredAt,
        )
        every {
            couponTemplateService.create(
                name = "10% 할인",
                type = CouponType.RATE,
                value = 10L,
                minOrderAmount = 10_000L,
                expiredAt = expiredAt,
            )
        } returns created

        val result = couponApplicationService.createCoupon(command)

        assertThat(result.id).isEqualTo(7L)
        assertThat(result.name).isEqualTo("10% 할인")
        assertThat(result.type).isEqualTo(CouponType.RATE)
        assertThat(result.value).isEqualTo(10L)
        assertThat(result.minOrderAmount).isEqualTo(10_000L)
        assertThat(result.expiredAt).isEqualTo(expiredAt)
        verify(exactly = 1) {
            couponTemplateService.create(
                name = "10% 할인",
                type = CouponType.RATE,
                value = 10L,
                minOrderAmount = 10_000L,
                expiredAt = expiredAt,
            )
        }
    }
}
