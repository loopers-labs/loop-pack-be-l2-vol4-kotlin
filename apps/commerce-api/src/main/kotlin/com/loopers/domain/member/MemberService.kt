package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberService(
    private val memberRepository: MemberRepository,
) {
    @Transactional
    fun signUp(command: MemberSignUpCommand): Member {
        if (memberRepository.existsByLoginId(command.loginId)) {
            throw CoreException(ErrorType.CONFLICT, "LoginId already exists.")
        }

        PasswordPolicy.validate(
            rawPassword = command.rawPassword,
            birthDate = command.birthDate,
        )

        val member = Member(
            loginId = command.loginId,
            password = PasswordEncoder.encode(command.rawPassword),
            name = command.name,
            birthDate = command.birthDate,
            email = command.email,
        )

        return memberRepository.save(member)
    }
}
