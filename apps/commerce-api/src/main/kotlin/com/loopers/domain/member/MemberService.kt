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

    @Transactional(readOnly = true)
    fun getMyInfo(
        loginId: String,
        rawPassword: String,
    ): Member {
        val member = memberRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Member not found.")

        if (!PasswordEncoder.matches(rawPassword, member.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "Member credentials do not match.")
        }

        return member
    }

    @Transactional
    fun updatePassword(
        loginId: String,
        rawPassword: String,
        newRawPassword: String,
    ) {
        val member = memberRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Member not found.")

        if (!PasswordEncoder.matches(rawPassword, member.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "Member credentials do not match.")
        }
        if (PasswordEncoder.matches(newRawPassword, member.password)) {
            throw CoreException(ErrorType.BAD_REQUEST, "New password must be different from current password.")
        }

        PasswordPolicy.validate(
            rawPassword = newRawPassword,
            birthDate = member.birthDate,
        )

        member.updatePassword(PasswordEncoder.encode(newRawPassword))
    }
}
