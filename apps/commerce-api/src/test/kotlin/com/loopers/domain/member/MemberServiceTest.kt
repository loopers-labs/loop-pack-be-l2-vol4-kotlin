package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class MemberServiceTest {
    @DisplayName("회원가입")
    @Nested
    inner class SignUp {
        private val memberRepository = FakeMemberRepository()
        private val memberService = MemberService(memberRepository)

        @DisplayName("회원가입 정보가 유효하면 비밀번호를 암호화해 회원을 저장한다")
        @Test
        fun savesMemberWithEncodedPassword_whenSignUpCommandIsValid() {
            val rawPassword = "Loopers123!"
            val command = createSignUpCommand(rawPassword = rawPassword)

            val member = memberService.signUp(command)

            assertThat(memberRepository.members).containsExactly(member)
            assertThat(member.password).isNotEqualTo(rawPassword)
            assertThat(PasswordEncoder.matches(rawPassword, member.password)).isTrue()
        }

        @DisplayName("이미 가입된 로그인 ID 로 회원가입하면 실패한다")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            memberRepository.save(createMember(loginId = "loopers123"))
            val command = createSignUpCommand(loginId = "loopers123")

            val result = assertThrows<CoreException> {
                memberService.signUp(command)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("비밀번호 정책을 만족하지 않으면 실패한다")
        @Test
        fun throwsBadRequest_whenPasswordDoesNotSatisfyPolicy() {
            val command = createSignUpCommand(rawPassword = "short")

            val result = assertThrows<CoreException> {
                memberService.signUp(command)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(memberRepository.members).isEmpty()
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

        private fun createMember(
            loginId: String = "loopers123",
            password: String = "encodedPassword",
            name: String = "gunyoung",
            birthDate: LocalDate = LocalDate.of(1995, 5, 20),
            email: String = "loopers@gmail.com",
        ): Member =
            Member(
                loginId = loginId,
                password = password,
                name = name,
                birthDate = birthDate,
                email = email,
            )
    }

    @DisplayName("내 정보 조회")
    @Nested
    inner class GetMyInfo {
        private val memberRepository = FakeMemberRepository()
        private val memberService = MemberService(memberRepository)

        @DisplayName("로그인 ID 와 비밀번호가 유효하면 회원 정보를 반환한다")
        @Test
        fun returnsMember_whenCredentialsAreValid() {
            val rawPassword = "Loopers123!"
            val member = createMember(
                loginId = "loopers123",
                password = PasswordEncoder.encode(rawPassword),
            )
            memberRepository.save(member)

            val result = memberService.getMyInfo("loopers123", rawPassword)

            assertThat(result).isEqualTo(member)
        }

        @DisplayName("로그인 ID 에 영문과 숫자 외 문자가 포함되면 실패한다")
        @Test
        fun throwsBadRequest_whenLoginIdContainsNonAlphanumericCharacters() {
            val result = assertThrows<CoreException> {
                memberService.getMyInfo("loopers-123", "Loopers123!")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("비밀번호가 일치하지 않으면 실패한다")
        @Test
        fun throwsUnauthorized_whenPasswordDoesNotMatch() {
            memberRepository.save(
                createMember(
                    loginId = "loopers123",
                    password = PasswordEncoder.encode("Loopers123!"),
                ),
            )

            val result = assertThrows<CoreException> {
                memberService.getMyInfo("loopers123", "Wrong123!")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }
    }

    private fun createMember(
        loginId: String = "loopers123",
        password: String = "encodedPassword",
        name: String = "gunyoung",
        birthDate: LocalDate = LocalDate.of(1995, 5, 20),
        email: String = "loopers@gmail.com",
    ): Member =
        Member(
            loginId = loginId,
            password = password,
            name = name,
            birthDate = birthDate,
            email = email,
        )

    private class FakeMemberRepository : MemberRepository {
        val members = mutableListOf<Member>()

        override fun existsByLoginId(loginId: String): Boolean {
            return members.any { it.loginId == loginId }
        }

        override fun findByLoginId(loginId: String): Member? {
            return members.find { it.loginId == loginId }
        }

        override fun save(member: Member): Member {
            members.add(member)
            return member
        }
    }
}
