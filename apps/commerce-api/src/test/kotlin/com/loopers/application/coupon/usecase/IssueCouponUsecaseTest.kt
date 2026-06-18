package com.loopers.application.coupon.usecase

import com.loopers.application.coupon.InMemoryCouponRepository
import com.loopers.application.coupon.InMemoryUserCouponRepository
import com.loopers.application.coupon.IssueCouponCommand
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.UserService
import com.loopers.domain.withId
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

class IssueCouponUsecaseTest {
    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    inner class Execute {
        @DisplayName("유효한 템플릿이면 AVAILABLE 상태의 쿠폰이 발급된다.")
        @Test
        fun issuesCoupon_whenTemplateIsValid() {
            val fixture = Fixture()
            val coupon = fixture.saveCoupon()

            val issued = fixture.issueCouponUsecase.execute(fixture.command(coupon.id))

            assertAll(
                { assertThat(issued.couponId).isEqualTo(coupon.id) },
                { assertThat(issued.status).isEqualTo(UserCouponStatus.AVAILABLE) },
            )
        }

        @DisplayName("존재하지 않는 템플릿이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenTemplateDoesNotExist() {
            val fixture = Fixture()

            val exception = assertThrows<CoreException> { fixture.issueCouponUsecase.execute(fixture.command(999L)) }

            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("만료된 템플릿이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenTemplateIsExpired() {
            val fixture = Fixture()
            val coupon = fixture.saveCoupon(expiredAt = ZonedDateTime.now().minusDays(1))

            val exception = assertThrows<CoreException> { fixture.issueCouponUsecase.execute(fixture.command(coupon.id)) }

            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("이미 발급받은 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenAlreadyIssued() {
            val fixture = Fixture()
            val coupon = fixture.saveCoupon()
            fixture.issueCouponUsecase.execute(fixture.command(coupon.id))

            val exception = assertThrows<CoreException> { fixture.issueCouponUsecase.execute(fixture.command(coupon.id)) }

            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    private class Fixture {
        private val userRepository = InMemoryUserRepository()
        val couponRepository = InMemoryCouponRepository()
        val userCouponRepository = InMemoryUserCouponRepository()
        val issueCouponUsecase = IssueCouponUsecase(
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

        fun saveCoupon(expiredAt: ZonedDateTime = ZonedDateTime.now().plusDays(30)): CouponModel {
            return couponRepository.save(
                CouponModel(
                    name = "테스트 쿠폰",
                    type = CouponType.FIXED,
                    discountValue = BigDecimal("1000"),
                    minOrderAmount = null,
                    expiredAt = expiredAt,
                ),
            )
        }

        fun command(couponId: Long) = IssueCouponCommand(loginId = "tester", password = "Password1!", couponId = couponId)
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
