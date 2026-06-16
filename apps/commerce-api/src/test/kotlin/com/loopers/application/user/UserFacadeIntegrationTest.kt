package com.loopers.application.user

import com.loopers.application.coupon.CouponService
import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.UserAccountService
import com.loopers.fixture.user.UserFixture
import com.loopers.infrastructure.member.MemberJpaRepository
import com.loopers.infrastructure.member.MemberRepositoryImpl
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    UserFacade::class,
    UserService::class,
    UserAccountService::class,
    MemberRepositoryImpl::class,
    MySqlTestContainersConfig::class,
    DataSourceConfig::class,
)
class UserFacadeIntegrationTest @Autowired constructor(
    private val userFacade: UserFacade,
    private val memberJpaRepository: MemberJpaRepository,
) {
    @MockitoBean
    private lateinit var couponService: CouponService

    @DisplayName("회원가입")
    @Nested
    inner class SignUp {
        @DisplayName("회원가입이 성공하면 암호화된 비밀번호로 회원을 저장한다")
        @Test
        fun savesUserWithEncodedPassword_whenSignUpCommandIsValid() {
            val command = UserFixture.createSignUpCommand()

            val result = userFacade.signUp(command)
            val savedUser = memberJpaRepository.findAll().single()

            assertAll(
                { assertThat(result.loginId).isEqualTo(command.loginId) },
                { assertThat(savedUser.password).isNotEqualTo(command.rawPassword) },
                { assertThat(PasswordEncoder.matches(command.rawPassword, savedUser.password)).isTrue() },
            )
        }

        @DisplayName("이미 가입된 로그인 ID 로 회원가입하면 실패한다")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            userFacade.signUp(UserFixture.createSignUpCommand(loginId = "loopers123"))

            val result = assertThrows<CoreException> {
                userFacade.signUp(
                    UserFixture.createSignUpCommand(
                        loginId = "loopers123",
                        email = "other@gmail.com",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }
}
