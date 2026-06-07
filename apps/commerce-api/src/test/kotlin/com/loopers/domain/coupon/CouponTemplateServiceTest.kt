package com.loopers.domain.coupon

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class CouponTemplateServiceTest {

    private lateinit var couponTemplateRepositoryPort: CouponTemplateRepositoryPort
    private lateinit var couponTemplateService: CouponTemplateService

    private val expiredAt = LocalDateTime.parse("2026-12-31T23:59:59")

    @BeforeEach
    fun setUp() {
        couponTemplateRepositoryPort = mockk()
        couponTemplateService = CouponTemplateService(couponTemplateRepositoryPort)
    }

    @DisplayName("getById를 호출할 때, ")
    @Nested
    inner class GetById {
        @DisplayName("쿠폰 템플릿이 존재하면, 도메인 객체를 반환한다.")
        @Test
        fun returnsTemplate_whenExists() {
            val template = CouponTemplate(
                id = 1L,
                name = "10% 할인",
                type = CouponType.RATE,
                value = 10L,
                expiredAt = expiredAt,
            )
            every { couponTemplateRepositoryPort.findById(1L) } returns template

            val result = couponTemplateService.getById(1L)

            assertThat(result).isEqualTo(template)
        }

        @DisplayName("쿠폰 템플릿이 없으면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            every { couponTemplateRepositoryPort.findById(any()) } returns null

            val result = assertThrows<CoreException> { couponTemplateService.getById(9999L) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("getAll을 호출할 때, ")
    @Nested
    inner class GetAll {
        @DisplayName("Repository의 findAll 결과(PageResult)를 그대로 반환한다.")
        @Test
        fun returnsPageResult_fromRepository() {
            val pageRequest = PageRequest(page = 0, size = 20)
            val pageResult = PageResult.of(
                items = listOf(
                    CouponTemplate(id = 2L, name = "B", type = CouponType.FIXED, value = 1_000L, expiredAt = expiredAt),
                    CouponTemplate(id = 1L, name = "A", type = CouponType.RATE, value = 10L, expiredAt = expiredAt),
                ),
                pageRequest = pageRequest,
                totalElements = 2L,
            )
            every { couponTemplateRepositoryPort.findAll(pageRequest) } returns pageResult

            val result = couponTemplateService.getAll(pageRequest)

            assertThat(result).isEqualTo(pageResult)
            verify(exactly = 1) { couponTemplateRepositoryPort.findAll(pageRequest) }
        }
    }

    @DisplayName("create를 호출할 때, ")
    @Nested
    inner class Create {
        @DisplayName("유효한 값이면, 도메인 불변식을 만족하는 템플릿을 save하고 결과를 반환한다.")
        @Test
        fun savesTemplate_whenValid() {
            val saved = CouponTemplate(
                id = 1L,
                name = "1만원 할인",
                type = CouponType.FIXED,
                value = 10_000L,
                minOrderAmount = 30_000L,
                expiredAt = expiredAt,
            )
            val captured = slot<CouponTemplate>()
            every { couponTemplateRepositoryPort.save(capture(captured)) } returns saved

            val result = couponTemplateService.create(
                name = "1만원 할인",
                type = CouponType.FIXED,
                value = 10_000L,
                minOrderAmount = 30_000L,
                expiredAt = expiredAt,
            )

            assertThat(result).isEqualTo(saved)
            assertThat(captured.captured.id).isEqualTo(0L)
            assertThat(captured.captured.name).isEqualTo("1만원 할인")
            assertThat(captured.captured.type).isEqualTo(CouponType.FIXED)
            verify(exactly = 1) { couponTemplateRepositoryPort.save(any()) }
        }

        @DisplayName("RATE 할인율이 범위를 벗어나면, 불변식 위반으로 save하지 않고 예외가 발생한다.")
        @Test
        fun throwsAndDoesNotSave_whenRateOutOfRange() {
            assertThrows<IllegalArgumentException> {
                couponTemplateService.create(
                    name = "잘못된 할인율",
                    type = CouponType.RATE,
                    value = 150L,
                    minOrderAmount = 0L,
                    expiredAt = expiredAt,
                )
            }

            verify(exactly = 0) { couponTemplateRepositoryPort.save(any()) }
        }
    }

    @DisplayName("delete를 호출할 때, ")
    @Nested
    inner class Delete {
        @DisplayName("Repository.delete가 1(삭제됨)을 반환하면, 예외 없이 정상 종료한다.")
        @Test
        fun succeeds_whenRowDeleted() {
            every { couponTemplateRepositoryPort.delete(1L) } returns 1

            couponTemplateService.delete(1L)

            verify(exactly = 1) { couponTemplateRepositoryPort.delete(1L) }
        }

        @DisplayName("Repository.delete가 0(삭제된 row 없음)을 반환하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenNoRowDeleted() {
            every { couponTemplateRepositoryPort.delete(9999L) } returns 0

            val result = assertThrows<CoreException> { couponTemplateService.delete(9999L) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
