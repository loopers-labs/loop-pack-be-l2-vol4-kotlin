package com.loopers.application.coupon

import com.loopers.application.coupon.dto.CouponCreateCommand
import com.loopers.domain.coupon.enums.DiscountType
import com.loopers.domain.coupon.model.Coupon
import com.loopers.domain.coupon.model.CouponIssue
import com.loopers.domain.coupon.repository.CouponIssueRepository
import com.loopers.domain.coupon.repository.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime

class CouponServiceTest {
    @DisplayName("쿠폰 등록")
    @Nested
    inner class CreateCoupon {
        @DisplayName("쿠폰 등록 요청이 유효하면 쿠폰을 저장한다")
        @Test
        fun savesCoupon_whenCommandIsValid() {
            val couponRepository = FakeCouponRepository()
            val couponService = CouponService(couponRepository, FakeCouponIssueRepository())
            val command = createCommand()

            val result = couponService.createCoupon(command)

            assertAll(
                { assertThat(result.id).isEqualTo(1L) },
                { assertThat(result.name).isEqualTo(command.name) },
                { assertThat(result.type).isEqualTo(command.type) },
                { assertThat(result.discountValue).isEqualTo(command.discountValue) },
                { assertThat(result.minOrderAmount).isEqualTo(command.minOrderAmount) },
                { assertThat(couponRepository.coupons).hasSize(1) },
            )
        }

        @DisplayName("이미 존재하는 쿠폰명으로 쿠폰을 등록할 수 없다")
        @Test
        fun throwsConflict_whenCouponNameAlreadyExists() {
            val couponRepository = FakeCouponRepository()
            val couponService = CouponService(couponRepository, FakeCouponIssueRepository())
            val command = createCommand()
            couponService.createCoupon(command)

            val result = assertThrows<CoreException> {
                couponService.createCoupon(command)
            }

            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(couponRepository.coupons).hasSize(1) },
            )
        }
    }

    private class FakeCouponRepository : CouponRepository {
        val coupons = mutableListOf<Coupon>()
        private var sequence = 1L

        override fun findById(couponId: Long): Coupon? {
            return coupons.find { it.id == couponId }
        }

        override fun findDisplayable(page: Int, size: Int): Page<Coupon> {
            val pageRequest = PageRequest.of(page, size)
            return PageImpl(
                coupons.filterNot(Coupon::isDeleted),
                pageRequest,
                coupons.count { !it.isDeleted }.toLong(),
            )
        }

        override fun save(coupon: Coupon): Coupon {
            val saved = if (coupon.id == 0L) {
                Coupon(
                    id = sequence++,
                    name = coupon.name,
                    type = coupon.type,
                    discountValue = coupon.discountValue,
                    minOrderAmount = coupon.minOrderAmount,
                    expiredAt = coupon.expiredAt,
                    isDeleted = coupon.isDeleted,
                )
            } else {
                coupon
            }

            coupons.removeIf { it.id == saved.id }
            coupons.add(saved)
            return saved
        }

        override fun update(coupon: Coupon): Coupon {
            coupons.removeIf { it.id == coupon.id }
            coupons.add(coupon)
            return coupon
        }

        override fun existsByName(name: String): Boolean {
            return coupons.any { it.name == name }
        }

        override fun existsByNameAndIdNot(name: String, couponId: Long): Boolean {
            return coupons.any { it.name == name && it.id != couponId }
        }
    }

    private class FakeCouponIssueRepository : CouponIssueRepository {
        override fun save(issue: CouponIssue): CouponIssue {
            return issue
        }

        override fun findById(issueId: Long): CouponIssue? {
            return null
        }

        override fun findByIdForUpdate(issueId: Long): CouponIssue? {
            return findById(issueId)
        }

        override fun findAllByMemberId(memberId: Long): List<CouponIssue> {
            return emptyList()
        }

        override fun findAllByCouponId(couponId: Long, page: Int, size: Int): Page<CouponIssue> {
            return PageImpl(emptyList(), PageRequest.of(page, size), 0)
        }

        override fun existsByCouponId(couponId: Long): Boolean {
            return false
        }
    }

    private fun createCommand(
        name: String = "신규가입 10% 할인",
        type: DiscountType = DiscountType.RATE,
        discountValue: Long = 10L,
        minOrderAmount: Long? = 10_000L,
        expiredAt: ZonedDateTime = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
    ): CouponCreateCommand {
        return CouponCreateCommand(
            name = name,
            type = type,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
    }
}
