package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.fixture.member.MemberFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
            val command = MemberFixture.createSignUpCommand(rawPassword = rawPassword)

            val member = memberService.signUp(command)

            assertThat(memberRepository.members).containsExactly(member)
            assertThat(member.password).isNotEqualTo(rawPassword)
            assertThat(PasswordEncoder.matches(rawPassword, member.password)).isTrue()
        }

        @DisplayName("이미 가입된 로그인 ID 로 회원가입하면 실패한다")
        @Test
        fun throwsConflict_whenLoginIdAlreadyExists() {
            memberRepository.save(MemberFixture.createMember(loginId = "loopers123"))
            val command = MemberFixture.createSignUpCommand(loginId = "loopers123")

            val result = assertThrows<CoreException> {
                memberService.signUp(command)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("비밀번호 정책을 만족하지 않으면 실패한다")
        @Test
        fun throwsBadRequest_whenPasswordDoesNotSatisfyPolicy() {
            val command = MemberFixture.createSignUpCommand(rawPassword = "short")

            val result = assertThrows<CoreException> {
                memberService.signUp(command)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(memberRepository.members).isEmpty()
        }
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
            val member = MemberFixture.createMember(
                loginId = "loopers123",
                password = PasswordEncoder.encode(rawPassword),
            )
            memberRepository.save(member)

            val result = memberService.getMyInfo("loopers123", rawPassword)

            assertThat(result).isEqualTo(member)
        }

        @DisplayName("가입되지 않은 로그인 ID 로 조회하면 실패한다")
        @Test
        fun throwsNotFound_whenLoginIdDoesNotExist() {
            val result = assertThrows<CoreException> {
                memberService.getMyInfo("loopers-123", "Loopers123!")
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("비밀번호가 일치하지 않으면 실패한다")
        @Test
        fun throwsUnauthorized_whenPasswordDoesNotMatch() {
            memberRepository.save(
                MemberFixture.createMember(
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

    @DisplayName("비밀번호 수정")
    @Nested
    inner class UpdatePassword {
        private val memberRepository = FakeMemberRepository()
        private val memberService = MemberService(memberRepository)

        @DisplayName("기존 비밀번호가 일치하고 새 비밀번호가 유효하면 비밀번호를 변경한다")
        @Test
        fun updatesPassword_whenCurrentPasswordMatchesAndNewPasswordIsValid() {
            val currentPassword = "Loopers123!"
            val newPassword = "NewLoopers1!"
            val member = MemberFixture.createMember(
                loginId = "loopers123",
                password = PasswordEncoder.encode(currentPassword),
            )
            memberRepository.save(member)

            memberService.updatePassword(
                loginId = "loopers123",
                rawPassword = currentPassword,
                newRawPassword = newPassword,
            )

            assertThat(PasswordEncoder.matches(newPassword, member.password)).isTrue()
        }

        @DisplayName("기존 비밀번호가 일치하지 않으면 실패한다")
        @Test
        fun throwsUnauthorized_whenCurrentPasswordDoesNotMatch() {
            memberRepository.save(
                MemberFixture.createMember(
                    loginId = "loopers123",
                    password = PasswordEncoder.encode("Loopers123!"),
                ),
            )

            val result = assertThrows<CoreException> {
                memberService.updatePassword(
                    loginId = "loopers123",
                    rawPassword = "Wrong123!",
                    newRawPassword = "NewLoopers1!",
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.UNAUTHORIZED)
        }

        @DisplayName("가입되지 않은 로그인 ID 로 비밀번호를 수정하면 실패한다")
        @Test
        fun throwsNotFound_whenLoginIdDoesNotExist() {
            val result = assertThrows<CoreException> {
                memberService.updatePassword(
                    loginId = "loopers-123",
                    rawPassword = "Loopers123!",
                    newRawPassword = "NewLoopers1!",
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("새 비밀번호가 정책을 만족하지 않으면 실패한다")
        @Test
        fun throwsBadRequest_whenNewPasswordDoesNotSatisfyPolicy() {
            memberRepository.save(
                MemberFixture.createMember(
                    loginId = "loopers123",
                    password = PasswordEncoder.encode("Loopers123!"),
                ),
            )

            val result = assertThrows<CoreException> {
                memberService.updatePassword(
                    loginId = "loopers123",
                    rawPassword = "Loopers123!",
                    newRawPassword = "short",
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("현재 비밀번호와 같은 비밀번호로 변경하면 실패한다")
        @Test
        fun throwsBadRequest_whenNewPasswordIsSameAsCurrentPassword() {
            val currentPassword = "Loopers123!"
            memberRepository.save(
                MemberFixture.createMember(
                    loginId = "loopers123",
                    password = PasswordEncoder.encode(currentPassword),
                ),
            )

            val result = assertThrows<CoreException> {
                memberService.updatePassword(
                    loginId = "loopers123",
                    rawPassword = currentPassword,
                    newRawPassword = currentPassword,
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

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
