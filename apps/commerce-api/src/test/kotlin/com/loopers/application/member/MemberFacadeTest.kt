package com.loopers.application.member

import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberService
import com.loopers.domain.member.MemberSignUpCommand
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

class MemberFacadeTest {
    @DisplayName("회원가입")
    @Nested
    inner class SignUp {
        private val memberService = mock<MemberService>()
        private val memberFacade = MemberFacade(memberService)

        @DisplayName("회원가입이 성공하면 회원 정보를 반환한다")
        @Test
        fun returnsMemberInfo_whenMemberIsSignedUp() {
            val command = MemberSignUpCommand(
                loginId = "loopers123",
                rawPassword = "Loopers123!",
                name = "gunyoung",
                birthDate = LocalDate.of(1995, 5, 20),
                email = "loopers@gmail.com",
            )
            val member = Member(
                loginId = command.loginId,
                password = "encodedPassword",
                name = command.name,
                birthDate = command.birthDate,
                email = command.email,
            )
            whenever(memberService.signUp(command)).thenReturn(member)

            val result = memberFacade.signUp(command)

            assertThat(result.loginId).isEqualTo(member.loginId)
            assertThat(result.name).isEqualTo(member.name)
            assertThat(result.birthDate).isEqualTo(member.birthDate)
            assertThat(result.email).isEqualTo(member.email)
            verify(memberService).signUp(command)
        }
    }
}
