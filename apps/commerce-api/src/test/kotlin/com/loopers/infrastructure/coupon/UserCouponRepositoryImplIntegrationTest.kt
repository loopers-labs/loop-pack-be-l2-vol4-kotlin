package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest
class UserCouponRepositoryImplIntegrationTest @Autowired constructor(
    private val userCouponRepository: UserCouponRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("발급된 쿠폰 저장 시, ")
    @Nested
    inner class Save {
        @DisplayName("미사용 상태로 저장되며 usedAt 은 null 이다.")
        @Test
        fun save_persistsUnused() {
            // act
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))

            // assert
            val found = userCouponRepository.findById(saved.id!!)
            assertAll(
                { assertThat(found?.userId).isEqualTo(1L) },
                { assertThat(found?.couponId).isEqualTo(10L) },
                { assertThat(found?.usedAt).isNull() },
                { assertThat(found?.isUsed()).isFalse() },
            )
        }
    }

    @DisplayName("쿠폰 사용 처리(useIfNotUsed) 시, ")
    @Nested
    inner class UseIfNotUsed {
        @DisplayName("본인 소유의 미사용 발급분이면 true 를 반환하고 used_at 을 기록한다.")
        @Test
        fun returnsTrue_whenOwnedAndNotUsed() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))
            val at = LocalDateTime.of(2026, 6, 8, 10, 0)

            // act
            val result = userCouponRepository.useIfNotUsed(id = saved.id!!, userId = 1L, usedAt = at)

            // assert
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(result).isTrue() },
                { assertThat(entity?.usedAt).isEqualTo(at) },
            )
        }

        @DisplayName("이미 사용된 발급분이면 false 를 반환하고 used_at 을 변경하지 않는다.")
        @Test
        fun returnsFalse_whenAlreadyUsed() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))
            val firstAt = LocalDateTime.of(2026, 6, 8, 10, 0)
            userCouponRepository.useIfNotUsed(id = saved.id!!, userId = 1L, usedAt = firstAt)

            val secondAt = LocalDateTime.of(2026, 6, 8, 11, 0)

            // act
            val result = userCouponRepository.useIfNotUsed(id = saved.id!!, userId = 1L, usedAt = secondAt)

            // assert
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(result).isFalse() },
                { assertThat(entity?.usedAt).isEqualTo(firstAt) },
            )
        }

        @DisplayName("다른 유저가 사용하려고 하면 false 를 반환하고 상태를 변경하지 않는다.")
        @Test
        fun returnsFalse_whenOwnedByAnotherUser() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))
            val at = LocalDateTime.of(2026, 6, 8, 10, 0)

            // act
            val result = userCouponRepository.useIfNotUsed(id = saved.id!!, userId = 999L, usedAt = at)

            // assert
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(result).isFalse() },
                { assertThat(entity?.usedAt).isNull() },
            )
        }

        @DisplayName("존재하지 않는 ID이면 false 를 반환한다.")
        @Test
        fun returnsFalse_whenNotFound() {
            // act
            val result = userCouponRepository.useIfNotUsed(
                id = 999_999L,
                userId = 1L,
                usedAt = LocalDateTime.now(),
            )

            // assert
            assertThat(result).isFalse()
        }
    }

    @DisplayName("쿠폰 사용 취소(cancelUseIfUsed) 시, ")
    @Nested
    inner class CancelUseIfUsed {
        @DisplayName("본인 소유의 사용된 발급분이면 true 를 반환하고 used_at 을 null 로 되돌린다.")
        @Test
        fun returnsTrue_whenOwnedAndAlreadyUsed() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))
            userCouponRepository.useIfNotUsed(
                id = saved.id!!,
                userId = 1L,
                usedAt = LocalDateTime.of(2026, 6, 8, 10, 0),
            )

            // act
            val result = userCouponRepository.cancelUseIfUsed(id = saved.id!!, userId = 1L)

            // assert
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(result).isTrue() },
                { assertThat(entity?.usedAt).isNull() },
            )
        }

        @DisplayName("미사용 발급분이면 false 를 반환하고 상태를 변경하지 않는다.")
        @Test
        fun returnsFalse_whenNotUsed() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))

            // act
            val result = userCouponRepository.cancelUseIfUsed(id = saved.id!!, userId = 1L)

            // assert
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(result).isFalse() },
                { assertThat(entity?.usedAt).isNull() },
            )
        }

        @DisplayName("다른 유저가 취소하려고 하면 false 를 반환하고 상태를 변경하지 않는다.")
        @Test
        fun returnsFalse_whenOwnedByAnotherUser() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))
            val firstAt = LocalDateTime.of(2026, 6, 8, 10, 0)
            userCouponRepository.useIfNotUsed(id = saved.id!!, userId = 1L, usedAt = firstAt)

            // act
            val result = userCouponRepository.cancelUseIfUsed(id = saved.id!!, userId = 999L)

            // assert
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(result).isFalse() },
                { assertThat(entity?.usedAt).isEqualTo(firstAt) },
            )
        }

        @DisplayName("존재하지 않는 ID이면 false 를 반환한다.")
        @Test
        fun returnsFalse_whenNotFound() {
            // act
            val result = userCouponRepository.cancelUseIfUsed(id = 999_999L, userId = 1L)

            // assert
            assertThat(result).isFalse()
        }

        @DisplayName("사용→취소→재사용 흐름이 정상 동작한다.")
        @Test
        fun useThenCancelThenUseAgain() {
            // arrange
            val saved = userCouponRepository.save(UserCoupon(userId = 1L, couponId = 10L))
            val firstAt = LocalDateTime.of(2026, 6, 8, 10, 0)
            val secondAt = LocalDateTime.of(2026, 6, 8, 11, 0)

            // act
            val useResult1 = userCouponRepository.useIfNotUsed(id = saved.id!!, userId = 1L, usedAt = firstAt)
            val cancelResult = userCouponRepository.cancelUseIfUsed(id = saved.id!!, userId = 1L)
            val useResult2 = userCouponRepository.useIfNotUsed(id = saved.id!!, userId = 1L, usedAt = secondAt)

            // assert
            val entity = userCouponJpaRepository.findByIdAndDeletedAtIsNull(saved.id!!)
            assertAll(
                { assertThat(useResult1).isTrue() },
                { assertThat(cancelResult).isTrue() },
                { assertThat(useResult2).isTrue() },
                { assertThat(entity?.usedAt).isEqualTo(secondAt) },
            )
        }
    }
}
