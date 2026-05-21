package com.loopers.application.member

import com.loopers.domain.member.MemberService
import com.loopers.fixture.member.MemberFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MemberFacadeTest {
    @DisplayName("회원가입")
    @Nested
    inner class SignUp {
        private val memberService = mock<MemberService>()
        private val memberFacade = MemberFacade(memberService)

        @DisplayName("회원가입이 성공하면 회원 정보를 반환한다")
        @Test
        fun returnsMemberInfo_whenMemberIsSignedUp() {
            val command = MemberFixture.createSignUpCommand()
            val member = MemberFixture.createMember(command)
            whenever(memberService.signUp(command)).thenReturn(member)

            val result = memberFacade.signUp(command)

            assertThat(result.loginId).isEqualTo(member.loginId)
            assertThat(result.name).isEqualTo(member.name)
            assertThat(result.birthDate).isEqualTo(member.birthDate)
            assertThat(result.email).isEqualTo(member.email)
            verify(memberService).signUp(command)
        }
    }

    @DisplayName("비밀번호 수정")
    @Nested
    inner class UpdatePassword {
        private val memberService = mock<MemberService>()
        private val memberFacade = MemberFacade(memberService)

        @DisplayName("비밀번호 수정 요청을 서비스로 위임한다")
        @Test
        fun delegatesToMemberService_whenPasswordUpdateIsRequested() {
            memberFacade.updatePassword(
                loginId = "loopers123",
                rawPassword = "Loopers123!",
                newRawPassword = "NewLoopers1!",
            )

            verify(memberService).updatePassword(
                loginId = "loopers123",
                rawPassword = "Loopers123!",
                newRawPassword = "NewLoopers1!",
            )
        }
    }
}
