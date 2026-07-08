package com.loopers.domain.coupon

import com.loopers.infrastructure.coupon.CouponJpaRepository
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
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime

@SpringBootTest
class CouponServiceIntegrationTest @Autowired constructor(
    private val couponService: CouponService,
    private val couponJpaRepository: CouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val expiredAt: ZonedDateTime = ZonedDateTime.parse("2026-12-31T23:59:59+09:00")

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun register(
        name: String = "신규가입 10% 할인",
        discountType: DiscountType = DiscountType.RATE,
        discountValue: Long = 10,
        minOrderAmount: Long? = 10_000,
    ): CouponModel = couponService.register(
        name = name,
        discountType = discountType,
        discountValue = discountValue,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
    )

    @DisplayName("쿠폰 템플릿을 등록할 때,")
    @Nested
    inner class Register {
        @DisplayName("유효한 값을 주면, 신규 템플릿이 저장된다.")
        @Test
        fun savesNewCoupon_whenValuesAreValid() {
            // act
            val result = register(name = "신규가입 10% 할인", discountType = DiscountType.RATE, discountValue = 10)

            // assert
            assertAll(
                { assertThat(result.id).isNotNull() },
                { assertThat(result.name).isEqualTo("신규가입 10% 할인") },
                { assertThat(result.discountType).isEqualTo(DiscountType.RATE) },
                { assertThat(result.discountValue).isEqualTo(10L) },
                { assertThat(result.deletedAt).isNull() },
            )
        }
    }

    @DisplayName("쿠폰 템플릿을 ID로 조회할 때,")
    @Nested
    inner class GetById {
        @DisplayName("활성 템플릿이 존재하면, 해당 템플릿을 반환한다.")
        @Test
        fun returnsCoupon_whenActiveCouponExists() {
            // arrange
            val saved = register()

            // act
            val result = couponService.getById(saved.id)

            // assert
            assertThat(result.id).isEqualTo(saved.id)
        }

        @DisplayName("존재하지 않는 ID로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenCouponDoesNotExist() {
            val result = assertThrows<CoreException> { couponService.getById(999L) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("soft-deleted 템플릿을 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenCouponIsSoftDeleted() {
            // arrange
            val saved = register()
            couponService.delete(saved.id)

            // act
            val result = assertThrows<CoreException> { couponService.getById(saved.id) }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("쿠폰 템플릿 목록을 조회할 때,")
    @Nested
    inner class GetAll {
        @DisplayName("활성 템플릿만 페이징되어 반환된다.")
        @Test
        fun returnsActiveCoupons_whenSoftDeletedCouponsExist() {
            // arrange
            val active = register(name = "활성 쿠폰")
            val toDelete = register(name = "삭제될 쿠폰")
            couponService.delete(toDelete.id)

            // act
            val result = couponService.getAll(PageRequest.of(0, 10))

            // assert
            assertAll(
                { assertThat(result.totalElements).isEqualTo(1) },
                { assertThat(result.content.map { it.id }).containsExactly(active.id) },
            )
        }
    }

    @DisplayName("쿠폰 템플릿을 수정할 때,")
    @Nested
    inner class Update {
        @DisplayName("유효한 값을 주면, 템플릿 내용이 변경된다.")
        @Test
        fun updatesCoupon_whenValuesAreValid() {
            // arrange
            val saved = register(name = "기존 쿠폰", discountType = DiscountType.RATE, discountValue = 10)

            // act
            couponService.update(
                id = saved.id,
                name = "수정된 쿠폰",
                discountType = DiscountType.FIXED,
                discountValue = 2_000,
                minOrderAmount = 5_000,
                expiredAt = expiredAt,
            )

            // assert
            val reloaded = couponJpaRepository.findById(saved.id).orElseThrow()
            assertAll(
                { assertThat(reloaded.name).isEqualTo("수정된 쿠폰") },
                { assertThat(reloaded.discountType).isEqualTo(DiscountType.FIXED) },
                { assertThat(reloaded.discountValue).isEqualTo(2_000L) },
                { assertThat(reloaded.minOrderAmount).isEqualTo(5_000L) },
            )
        }

        @DisplayName("존재하지 않는 ID로 수정하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenCouponDoesNotExist() {
            val result = assertThrows<CoreException> {
                couponService.update(
                    id = 999L,
                    name = "수정된 쿠폰",
                    discountType = DiscountType.FIXED,
                    discountValue = 2_000,
                    minOrderAmount = null,
                    expiredAt = expiredAt,
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("쿠폰 템플릿을 삭제할 때,")
    @Nested
    inner class Delete {
        @DisplayName("활성 템플릿을 삭제하면, soft delete 처리된다.")
        @Test
        fun softDeletesCoupon_whenCouponIsActive() {
            // arrange
            val saved = register()

            // act
            couponService.delete(saved.id)

            // assert
            val reloaded = couponJpaRepository.findById(saved.id).orElseThrow()
            assertThat(reloaded.deletedAt).isNotNull()
        }

        @DisplayName("이미 삭제된 템플릿을 다시 삭제해도, 예외 없이 처리된다 (멱등).")
        @Test
        fun isIdempotent_whenCouponIsAlreadyDeleted() {
            // arrange
            val saved = register()
            couponService.delete(saved.id)

            // act + assert
            couponService.delete(saved.id)
        }
    }

    @DisplayName("발급 수량을 소진할 때,")
    @Nested
    inner class TryIssue {
        @DisplayName("직접 호출해도 쓰기 트랜잭션으로 발급 수량이 1 증가한다.")
        @Test
        fun incrementsIssuedQuantity_whenCalledDirectly() {
            // arrange: 클래스 기본 readOnly 트랜잭션을 상속하면 이 발급(UPDATE)이 막힐 수 있다.
            val saved = couponService.register(
                name = "선착순 쿠폰",
                discountType = DiscountType.RATE,
                discountValue = 10,
                minOrderAmount = null,
                expiredAt = expiredAt,
                issuableQuantity = 2,
            )

            // act
            val issued = couponService.tryIssue(saved.id)

            // assert
            assertThat(issued).isTrue()
            val reloaded = couponJpaRepository.findById(saved.id).orElseThrow()
            assertThat(reloaded.issuedQuantity).isEqualTo(1L)
        }
    }
}
