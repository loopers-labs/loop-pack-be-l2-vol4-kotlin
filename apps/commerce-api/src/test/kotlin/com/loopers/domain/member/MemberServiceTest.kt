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
            val command = MemberSignUpCommand(
                loginId = "loopers123",
                rawPassword = rawPassword,
                name = "gunyoung",
                birthDate = LocalDate.of(1995, 5, 20),
                email = "loopers@gmail.com",
            )

            val member = memberService.signUp(command)

            assertThat(memberRepository.members).containsExactly(member)
            assertThat(member.password).isNotEqualTo(rawPassword)
            assertThat(PasswordEncoder.matches(rawPassword, member.password)).isTrue()
        }

        @DisplayName("이미 가입된 로그인 ID 로 회원가입하면 실패한다")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            memberRepository.save(
                Member(
                    loginId = "loopers123",
                    password = "encodedPassword",
                    name = "gunyoung",
                    birthDate = LocalDate.of(1995, 5, 20),
                    email = "loopers@gmail.com",
                ),
            )
            val command = MemberSignUpCommand(
                loginId = "loopers123",
                rawPassword = "Loopers123!",
                name = "gunyoung",
                birthDate = LocalDate.of(1995, 5, 20),
                email = "loopers@gmail.com",
            )

            val result = assertThrows<CoreException> {
                memberService.signUp(command)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("비밀번호 정책을 만족하지 않으면 실패한다")
        @Test
        fun throwsBadRequest_whenPasswordDoesNotSatisfyPolicy() {
            val command = MemberSignUpCommand(
                loginId = "loopers123",
                rawPassword = "short",
                name = "gunyoung",
                birthDate = LocalDate.of(1995, 5, 20),
                email = "loopers@gmail.com",
            )

            val result = assertThrows<CoreException> {
                memberService.signUp(command)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(memberRepository.members).isEmpty()
        }
    }

    private class FakeMemberRepository : MemberRepository {
        val members = mutableListOf<Member>()

        override fun existsByLoginId(loginId: String): Boolean {
            return members.any { it.loginId == loginId }
        }

        override fun save(member: Member): Member {
            members.add(member)
            return member
        }
    }
}
