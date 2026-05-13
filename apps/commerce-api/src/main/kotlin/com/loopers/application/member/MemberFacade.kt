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
}
