package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.enums.DiscountType
import com.loopers.domain.coupon.model.Coupon
import com.loopers.domain.coupon.repository.CouponRepository
import com.loopers.infrastructure.coupon.repository.CouponJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.ZonedDateTime

@SpringBootTest
class CouponRepositoryIntegrationTest @Autowired constructor(
    private val couponRepository: CouponRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("쿠폰 저장")
    @Nested
    inner class Save {
        @DisplayName("쿠폰을 저장하면 JPA 엔티티로 영속화한다")
        @Test
        fun savesCoupon() {
            val coupon = createCoupon()

            val result = couponRepository.save(coupon)

            val savedEntity = couponJpaRepository.findAll().single()
            assertAll(
                { assertThat(result.id).isPositive() },
                { assertThat(result.name).isEqualTo(coupon.name) },
                { assertThat(savedEntity.name).isEqualTo(coupon.name) },
                { assertThat(savedEntity.type).isEqualTo(coupon.type) },
                { assertThat(savedEntity.discountValue).isEqualTo(coupon.discountValue) },
                { assertThat(savedEntity.minOrderAmount).isEqualTo(coupon.minOrderAmount) },
                { assertThat(savedEntity.expiredAt.toInstant()).isEqualTo(coupon.expiredAt.toInstant()) },
            )
        }

        @DisplayName("동일한 이름의 쿠폰 저장이 DB 제약에 걸리면 충돌 예외로 변환한다")
        @Test
        fun throwsConflict_whenCouponNameAlreadyExists() {
            couponRepository.save(createCoupon(name = "신규가입 10% 할인"))

            val result = assertThrows<CoreException> {
                couponRepository.save(createCoupon(name = "신규가입 10% 할인"))
            }

            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(couponJpaRepository.findAll()).hasSize(1) },
            )
        }
    }

    private fun createCoupon(
        name: String = "신규가입 10% 할인",
        type: DiscountType = DiscountType.RATE,
        discountValue: Long = 10L,
        minOrderAmount: Long? = 10_000L,
        expiredAt: ZonedDateTime = ZonedDateTime.parse("2099-12-31T23:59:59+09:00"),
    ): Coupon {
        return Coupon(
            name = name,
            type = type,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
        )
    }
}
