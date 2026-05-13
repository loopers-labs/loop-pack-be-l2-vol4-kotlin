package com.loopers.application.member

import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.member.MemberService
import com.loopers.domain.member.MemberSignUpCommand
import com.loopers.domain.member.PasswordEncoder
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
import java.time.LocalDate

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    MemberFacade::class,
    MemberService::class,
    MemberRepositoryImpl::class,
    MySqlTestContainersConfig::class,
    DataSourceConfig::class,
)
class MemberFacadeIntegrationTest @Autowired constructor(
    private val memberFacade: MemberFacade,
    private val memberJpaRepository: MemberJpaRepository,
) {
    @DisplayName("회원가입")
    @Nested
    inner class SignUp {
        @DisplayName("회원가입이 성공하면 암호화된 비밀번호로 회원을 저장한다")
        @Test
        fun savesMemberWithEncodedPassword_whenSignUpCommandIsValid() {
            val command = createSignUpCommand()

            val result = memberFacade.signUp(command)
            val savedMember = memberJpaRepository.findAll().single()

            assertAll(
                { assertThat(result.loginId).isEqualTo(command.loginId) },
                { assertThat(savedMember.password).isNotEqualTo(command.rawPassword) },
                { assertThat(PasswordEncoder.matches(command.rawPassword, savedMember.password)).isTrue() },
            )
        }

        @DisplayName("이미 가입된 로그인 ID 로 회원가입하면 실패한다")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            memberFacade.signUp(createSignUpCommand(loginId = "loopers123"))

            val result = assertThrows<CoreException> {
                memberFacade.signUp(createSignUpCommand(loginId = "loopers123", email = "other@gmail.com"))
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        private fun createSignUpCommand(
            loginId: String = "loopers123",
            rawPassword: String = "Loopers123!",
            name: String = "gunyoung",
            birthDate: LocalDate = LocalDate.of(1995, 5, 20),
            email: String = "loopers@gmail.com",
        ): MemberSignUpCommand =
            MemberSignUpCommand(
                loginId = loginId,
                rawPassword = rawPassword,
                name = name,
                birthDate = birthDate,
                email = email,
            )
    }
}
