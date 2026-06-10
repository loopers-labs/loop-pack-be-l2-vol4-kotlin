package com.loopers.application.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
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

@SpringBootTest
class CouponApplicationServiceIntegrationTest @Autowired constructor(
    private val couponApplicationService: CouponApplicationService,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("쿠폰 이름 변경 시, ")
    @Nested
    inner class RenameCoupon {
        @DisplayName("도메인 규칙에 따라 이름만 변경하고 정책은 유지한다.")
        @Test
        fun renamesCouponAndKeepsPolicy() {
            // arrange
            val saved = couponRepository.save(
                Coupon(name = "초기 이름", policy = DiscountPolicy.FixedAmount(1_000L)),
            )

            // act
            val result = couponApplicationService.renameCoupon(id = saved.id!!, name = "변경된 이름")

            // assert
            val entity = couponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            val policy = result.policy
            assertAll(
                { assertThat(result.name).isEqualTo("변경된 이름") },
                { assertThat(entity?.name).isEqualTo("변경된 이름") },
                { assertThat(policy).isInstanceOf(DiscountPolicy.FixedAmount::class.java) },
                { assertThat((policy as DiscountPolicy.FixedAmount).amount).isEqualTo(1_000L) },
                { assertThat(entity?.policyType).isEqualTo(DiscountPolicy.Type.FIXED_AMOUNT) },
                { assertThat(entity?.policyValue).isEqualTo(1_000L) },
            )
        }

        @DisplayName("존재하지 않는 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenCouponNotExists() {
            // act & assert
            val result = assertThrows<CoreException> {
                couponApplicationService.renameCoupon(id = 999_999L, name = "새 이름")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("빈 이름으로 변경하려고 하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenNameIsBlank() {
            // arrange
            val saved = couponRepository.save(
                Coupon(name = "초기 이름", policy = DiscountPolicy.FixedAmount(1_000L)),
            )

            // act & assert
            val result = assertThrows<CoreException> {
                couponApplicationService.renameCoupon(id = saved.id!!, name = "  ")
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("쿠폰 사용 처리 시, ")
    @Nested
    inner class UseCoupon {
        @DisplayName("본인 소유의 미사용 발급분이면 used_at 을 기록한다.")
        @Test
        fun usesOwnedUnusedCoupon() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))

            // act
            couponApplicationService.useCoupon(userId = 1L, userCouponId = saved.id!!)

            // assert
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertThat(entity?.usedAt).isNotNull()
        }

        @DisplayName("이미 사용된 발급분이면 CONFLICT 예외가 발생하고 used_at 을 변경하지 않는다.")
        @Test
        fun throwsConflict_whenAlreadyUsed() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))
            couponApplicationService.useCoupon(userId = 1L, userCouponId = saved.id!!)
            val firstUsedAt = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)?.usedAt

            // act & assert
            val result = assertThrows<CoreException> {
                couponApplicationService.useCoupon(userId = 1L, userCouponId = saved.id!!)
            }
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(entity?.usedAt).isEqualTo(firstUsedAt) },
            )
        }

        @DisplayName("다른 유저의 발급분이면 BAD_REQUEST 예외가 발생하고 상태를 변경하지 않는다.")
        @Test
        fun throwsBadRequest_whenOwnedByAnotherUser() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))

            // act & assert
            val result = assertThrows<CoreException> {
                couponApplicationService.useCoupon(userId = 999L, userCouponId = saved.id!!)
            }
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
                { assertThat(entity?.usedAt).isNull() },
            )
        }

        @DisplayName("존재하지 않는 발급분이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenNotFound() {
            // act & assert
            val result = assertThrows<CoreException> {
                couponApplicationService.useCoupon(userId = 1L, userCouponId = 999_999L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("쿠폰 사용 취소 시, ")
    @Nested
    inner class CancelCouponUse {
        @DisplayName("본인 소유의 사용된 발급분이면 used_at 을 null 로 되돌린다.")
        @Test
        fun cancelsOwnedUsedCoupon() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))
            couponApplicationService.useCoupon(userId = 1L, userCouponId = saved.id!!)

            // act
            couponApplicationService.cancelCouponUse(userId = 1L, userCouponId = saved.id!!)

            // assert
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertThat(entity?.usedAt).isNull()
        }

        @DisplayName("미사용 발급분이면 CONFLICT 예외가 발생하고 상태를 변경하지 않는다.")
        @Test
        fun throwsConflict_whenNotUsed() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))

            // act & assert
            val result = assertThrows<CoreException> {
                couponApplicationService.cancelCouponUse(userId = 1L, userCouponId = saved.id!!)
            }
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT) },
                { assertThat(entity?.usedAt).isNull() },
            )
        }

        @DisplayName("다른 유저가 취소하려고 하면 BAD_REQUEST 예외가 발생하고 상태를 변경하지 않는다.")
        @Test
        fun throwsBadRequest_whenOwnedByAnotherUser() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))
            couponApplicationService.useCoupon(userId = 1L, userCouponId = saved.id!!)
            val firstUsedAt = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)?.usedAt

            // act & assert
            val result = assertThrows<CoreException> {
                couponApplicationService.cancelCouponUse(userId = 999L, userCouponId = saved.id!!)
            }
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
                { assertThat(entity?.usedAt).isEqualTo(firstUsedAt) },
            )
        }

        @DisplayName("존재하지 않는 발급분이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenNotFound() {
            // act & assert
            val result = assertThrows<CoreException> {
                couponApplicationService.cancelCouponUse(userId = 1L, userCouponId = 999_999L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("사용→취소→재사용 흐름이 정상 동작한다.")
        @Test
        fun useThenCancelThenUseAgain() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))

            // act
            couponApplicationService.useCoupon(userId = 1L, userCouponId = saved.id!!)
            couponApplicationService.cancelCouponUse(userId = 1L, userCouponId = saved.id!!)
            couponApplicationService.useCoupon(userId = 1L, userCouponId = saved.id!!)

            // assert
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertThat(entity?.usedAt).isNotNull()
        }
    }
}
