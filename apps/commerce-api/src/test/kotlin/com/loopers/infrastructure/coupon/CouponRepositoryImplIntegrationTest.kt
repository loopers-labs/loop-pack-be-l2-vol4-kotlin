package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class CouponRepositoryImplIntegrationTest @Autowired constructor(
    private val couponRepository: CouponRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("쿠폰 저장 시, ")
    @Nested
    inner class Save {
        @DisplayName("정액 할인 쿠폰을 저장하고 조회하면 정책이 그대로 복원된다.")
        @Test
        fun save_persistsFixedAmountPolicy() {
            // arrange
            val coupon = Coupon(
                name = "1000원 할인",
                policy = DiscountPolicy.FixedAmount(1_000L),
            )

            // act
            val saved = couponRepository.save(coupon)
            val found = couponRepository.findById(saved.id!!)

            // assert
            val policy = found?.policy
            assertAll(
                { assertThat(found?.name).isEqualTo("1000원 할인") },
                { assertThat(policy).isInstanceOf(DiscountPolicy.FixedAmount::class.java) },
                { assertThat((policy as DiscountPolicy.FixedAmount).amount).isEqualTo(1_000L) },
            )
        }

        @DisplayName("정률 할인 쿠폰을 저장하고 조회하면 정책이 그대로 복원된다.")
        @Test
        fun save_persistsRatePolicy() {
            // arrange
            val coupon = Coupon(
                name = "10% 할인",
                policy = DiscountPolicy.Rate(10),
            )

            // act
            val saved = couponRepository.save(coupon)
            val found = couponRepository.findById(saved.id!!)

            // assert
            val policy = found?.policy
            assertAll(
                { assertThat(found?.name).isEqualTo("10% 할인") },
                { assertThat(policy).isInstanceOf(DiscountPolicy.Rate::class.java) },
                { assertThat((policy as DiscountPolicy.Rate).percent).isEqualTo(10) },
            )
        }

        @DisplayName("id를 가진 쿠폰의 이름을 변경 후 save 하면 기존 row 의 name 만 갱신되고 정책은 유지된다.")
        @Test
        fun save_updatesNameAndKeepsPolicy_whenExistingCoupon() {
            // arrange
            val saved = couponRepository.save(
                Coupon(name = "초기 이름", policy = DiscountPolicy.FixedAmount(1_000L)),
            )
            saved.rename("변경된 이름")

            // act
            val result = couponRepository.save(saved)

            // assert
            val entity = couponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            val policy = result.policy
            assertAll(
                { assertThat(result.id).isEqualTo(saved.id) },
                { assertThat(result.name).isEqualTo("변경된 이름") },
                { assertThat(entity?.name).isEqualTo("변경된 이름") },
                { assertThat(policy).isInstanceOf(DiscountPolicy.FixedAmount::class.java) },
                { assertThat((policy as DiscountPolicy.FixedAmount).amount).isEqualTo(1_000L) },
                { assertThat(entity?.policyType).isEqualTo(DiscountPolicy.Type.FIXED_AMOUNT) },
                { assertThat(entity?.policyValue).isEqualTo(1_000L) },
            )
        }
    }

    @DisplayName("쿠폰 조회 시, ")
    @Nested
    inner class FindById {
        @DisplayName("존재하지 않는 ID로 조회하면 null을 반환한다.")
        @Test
        fun returnsNull_whenNotFound() {
            // act
            val found = couponRepository.findById(999_999L)

            // assert
            assertThat(found).isNull()
        }
    }
}
