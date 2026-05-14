package com.loopers.application.member

import com.loopers.domain.member.MemberService
import com.loopers.domain.member.MemberSignUpCommand
import org.springframework.stereotype.Component

@Component
class MemberFacade(
    private val memberService: MemberService,
) {
    fun signUp(command: MemberSignUpCommand): MemberInfo {
        return memberService.signUp(command)
            .let { MemberInfo.from(it) }
    }

    fun getMyInfo(
        loginId: String,
        rawPassword: String,
    ): MemberInfo {
        return memberService.getMyInfo(loginId, rawPassword)
            .let { MemberInfo.from(it) }
    }

    fun updatePassword(
        loginId: String,
        rawPassword: String,
        newRawPassword: String,
    ) {
        memberService.updatePassword(
            loginId = loginId,
            rawPassword = rawPassword,
            newRawPassword = newRawPassword,
        )
    }
}
