package com.loopers.application.coupon.usecase

import com.loopers.application.coupon.InMemoryCouponRepository
import com.loopers.application.coupon.InMemoryUserCouponRepository
import com.loopers.application.coupon.MyCouponsCommand
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.UserService
import com.loopers.domain.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

class GetMyCouponsUsecaseTest {
    @DisplayName("내 쿠폰 목록은 AVAILABLE / USED / EXPIRED 상태를 함께 반환한다.")
    @Test
    fun returnsDerivedStatuses() {
        // arrange
        val fixture = Fixture()
        val now = ZonedDateTime.now()
        val available = fixture.saveCoupon(expiredAt = now.plusDays(1))
        val expired = fixture.saveCoupon(expiredAt = now.minusDays(1))
        val usedTemplate = fixture.saveCoupon(expiredAt = now.plusDays(1))

        fixture.userCouponRepository.save(UserCouponModel(userId = 1L, couponId = available.id))
        fixture.userCouponRepository.save(UserCouponModel(userId = 1L, couponId = expired.id))
        fixture.userCouponRepository.save(
            UserCouponModel(userId = 1L, couponId = usedTemplate.id).apply { use(coupon = usedTemplate, now = now) },
        )

        // act
        val coupons = fixture.getMyCouponsUsecase.execute(MyCouponsCommand(loginId = "tester", password = "Password1!"))

        // assert
        val statusByCouponId = coupons.associate { it.couponId to it.status }
        assertThat(statusByCouponId).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                available.id to UserCouponStatus.AVAILABLE,
                expired.id to UserCouponStatus.EXPIRED,
                usedTemplate.id to UserCouponStatus.USED,
            ),
        )
    }

    private class Fixture {
        private val userRepository = InMemoryUserRepository()
        val couponRepository = InMemoryCouponRepository()
        val userCouponRepository = InMemoryUserCouponRepository()
        val getMyCouponsUsecase = GetMyCouponsUsecase(
            userService = UserService(userRepository),
            couponRepository = couponRepository,
            userCouponRepository = userCouponRepository,
        )

        init {
            userRepository.save(
                UserModel(
                    loginId = "tester",
                    rawPassword = "Password1!",
                    name = "테스터",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "tester@loopers.com",
                ).withId(1L),
            )
        }

        fun saveCoupon(expiredAt: ZonedDateTime): CouponModel {
            return couponRepository.save(
                CouponModel(
                    name = "쿠폰",
                    type = CouponType.FIXED,
                    discountValue = BigDecimal("1000"),
                    minOrderAmount = null,
                    expiredAt = expiredAt,
                ),
            )
        }
    }

    private class InMemoryUserRepository : UserRepository {
        private val users = mutableMapOf<Long, UserModel>()

        override fun save(user: UserModel): UserModel {
            users[user.id] = user
            return user
        }

        override fun findById(id: Long): UserModel? {
            return users[id]
        }

        override fun findByLoginId(loginId: String): UserModel? {
            return users.values.firstOrNull { it.loginId == loginId }
        }

        override fun existsByLoginId(loginId: String): Boolean {
            return findByLoginId(loginId) != null
        }
    }
}
