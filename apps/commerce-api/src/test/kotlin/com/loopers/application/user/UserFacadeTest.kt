package com.loopers.application.user

import com.loopers.application.coupon.CouponService
import com.loopers.application.user.dto.UserInfo
import com.loopers.fixture.user.UserFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserFacadeTest {
    @DisplayName("회원가입")
    @Nested
    inner class SignUp {
        private val userService = mock<UserService>()
        private val couponService = mock<CouponService>()
        private val userFacade = UserFacade(userService, couponService)

        @DisplayName("회원가입이 성공하면 회원 정보를 반환한다")
        @Test
        fun returnsUserInfo_whenUserIsSignedUp() {
            val command = UserFixture.createSignUpCommand()
            val info = UserInfo.from(UserFixture.createUser(command))
            whenever(userService.signUp(command)).thenReturn(info)

            val result = userFacade.signUp(command)

            assertThat(result).isEqualTo(info)
            verify(userService).signUp(command)
        }
    }

    @DisplayName("비밀번호 수정")
    @Nested
    inner class UpdatePassword {
        private val userService = mock<UserService>()
        private val couponService = mock<CouponService>()
        private val userFacade = UserFacade(userService, couponService)

        @DisplayName("비밀번호 수정 요청을 서비스로 위임한다")
        @Test
        fun delegatesToUserService_whenPasswordUpdateIsRequested() {
            userFacade.updatePassword(
                loginId = "loopers123",
                rawPassword = "Loopers123!",
                newRawPassword = "NewLoopers1!",
            )

            verify(userService).updatePassword(
                loginId = "loopers123",
                rawPassword = "Loopers123!",
                newRawPassword = "NewLoopers1!",
            )
        }
    }
}
