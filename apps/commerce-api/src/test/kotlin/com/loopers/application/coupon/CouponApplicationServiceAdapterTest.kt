package com.loopers.application.coupon

import com.loopers.domain.auth.AuthService
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.CouponTemplate
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class CouponApplicationServiceAdapterTest {

    private lateinit var couponTemplateService: CouponTemplateService
    private lateinit var userCouponService: UserCouponService
    private lateinit var authService: AuthService
    private lateinit var couponApplicationService: CouponApplicationServiceAdapter

    private val expiredAt = LocalDateTime.parse("2026-12-31T23:59:59")

    @BeforeEach
    fun setUp() {
        couponTemplateService = mockk()
        userCouponService = mockk()
        authService = mockk()
        couponApplicationService = CouponApplicationServiceAdapter(couponTemplateService, userCouponService, authService)
    }

    @DisplayName("issueCoupon은 템플릿을 조회한 뒤 발급 서비스에 위임하고, 결과를 UserCouponResult로 매핑한다.")
    @Test
    fun issueCoupon_delegatesAndMapsResult() {
        val template = CouponTemplate(
            id = 4L,
            name = "1만원 할인",
            type = CouponType.FIXED,
            value = 10_000L,
            minOrderAmount = 30_000L,
            expiredAt = expiredAt,
        )
        val issued = UserCoupon(
            id = 11L,
            couponTemplateId = 4L,
            userId = 9L,
            status = CouponStatus.AVAILABLE,
            issuedAt = LocalDateTime.parse("2026-06-07T10:00:00"),
            usedAt = null,
        )
        every { couponTemplateService.getById(4L) } returns template
        every { userCouponService.issue(template, 9L, any()) } returns issued

        val result = couponApplicationService.issueCoupon(userId = 9L, couponId = 4L)

        assertThat(result.id).isEqualTo(11L)
        assertThat(result.couponTemplateId).isEqualTo(4L)
        assertThat(result.userId).isEqualTo(9L)
        assertThat(result.status).isEqualTo(CouponStatus.AVAILABLE)
        verify(exactly = 1) { couponTemplateService.getById(4L) }
        verify(exactly = 1) { userCouponService.issue(template, 9L, any()) }
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

    @DisplayName("getCoupon은 도메인 서비스의 getById를 호출하고, 결과를 CouponResult로 매핑한다.")
    @Test
    fun getCoupon_delegatesToServiceAndMapsResult() {
        val template = CouponTemplate(
            id = 3L,
            name = "1만원 할인",
            type = CouponType.FIXED,
            value = 10_000L,
            minOrderAmount = 30_000L,
            expiredAt = expiredAt,
        )
        every { couponTemplateService.getById(3L) } returns template

        val result = couponApplicationService.getCoupon(3L)

        assertThat(result.id).isEqualTo(3L)
        assertThat(result.name).isEqualTo("1만원 할인")
        assertThat(result.type).isEqualTo(CouponType.FIXED)
        assertThat(result.value).isEqualTo(10_000L)
        assertThat(result.minOrderAmount).isEqualTo(30_000L)
        assertThat(result.expiredAt).isEqualTo(expiredAt)
        verify(exactly = 1) { couponTemplateService.getById(3L) }
    }

    @DisplayName("deleteCoupon은 도메인 서비스의 delete를 id로 위임 호출한다.")
    @Test
    fun deleteCoupon_delegatesToService() {
        every { couponTemplateService.delete(5L) } returns Unit

        couponApplicationService.deleteCoupon(5L)

        verify(exactly = 1) { couponTemplateService.delete(5L) }
    }

    @DisplayName("getCouponIssues는 템플릿 존재 확인 후 발급 내역에 loginId를 매핑한 페이지를 반환한다.")
    @Test
    fun getCouponIssues_mapsLoginIdAndReturnsPage() {
        val template = CouponTemplate(
            id = 3L,
            name = "1만원 할인",
            type = CouponType.FIXED,
            value = 10_000L,
            minOrderAmount = 30_000L,
            expiredAt = expiredAt,
        )
        val issuedAt = LocalDateTime.parse("2026-06-07T10:00:00")
        val coupon1 = UserCoupon(
            id = 21L,
            couponTemplateId = 3L,
            userId = 9L,
            status = CouponStatus.AVAILABLE,
            issuedAt = issuedAt,
            usedAt = null,
        )
        val coupon2 = UserCoupon(
            id = 20L,
            couponTemplateId = 3L,
            userId = 8L,
            status = CouponStatus.USED,
            issuedAt = issuedAt,
            usedAt = LocalDateTime.parse("2026-06-07T11:00:00"),
        )
        val pageRequest = PageRequest(page = 0, size = 20)
        every { couponTemplateService.getById(3L) } returns template
        every { userCouponService.getByCouponTemplateId(3L, pageRequest) } returns
            PageResult.of(items = listOf(coupon1, coupon2), pageRequest = pageRequest, totalElements = 2L)
        every { authService.findLoginIdsByUserIds(listOf(9L, 8L)) } returns mapOf(9L to "hong", 8L to "kim")

        val result = couponApplicationService.getCouponIssues(3L, pageRequest)

        assertThat(result.totalElements).isEqualTo(2L)
        assertThat(result.items).hasSize(2)
        assertThat(result.items[0].id).isEqualTo(21L)
        assertThat(result.items[0].loginId).isEqualTo("hong")
        assertThat(result.items[0].status).isEqualTo(CouponStatus.AVAILABLE)
        assertThat(result.items[1].id).isEqualTo(20L)
        assertThat(result.items[1].loginId).isEqualTo("kim")
        assertThat(result.items[1].usedAt).isEqualTo(LocalDateTime.parse("2026-06-07T11:00:00"))
        verify(exactly = 1) { couponTemplateService.getById(3L) }
        verify(exactly = 1) { userCouponService.getByCouponTemplateId(3L, pageRequest) }
    }

    @DisplayName("getCouponIssues는 템플릿이 존재하지 않으면 예외를 전파하고 발급 내역을 조회하지 않는다.")
    @Test
    fun getCouponIssues_throwsWhenTemplateNotFound() {
        val pageRequest = PageRequest(page = 0, size = 20)
        every { couponTemplateService.getById(9999L) } throws
            CoreException(ErrorType.NOT_FOUND, "쿠폰 템플릿을 찾을 수 없습니다.")

        assertThrows<CoreException> {
            couponApplicationService.getCouponIssues(9999L, pageRequest)
        }

        verify(exactly = 0) { userCouponService.getByCouponTemplateId(any(), any()) }
    }
}
