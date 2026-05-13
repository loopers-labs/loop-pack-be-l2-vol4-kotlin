package com.loopers.application.member

import com.loopers.domain.member.Member
import java.time.LocalDate

data class MemberInfo(
    val loginId: String,
    val name: String,
    val birthDate: LocalDate,
    val email: String,
) {
    companion object {
        fun from(member: Member): MemberInfo {
            return MemberInfo(
                loginId = member.loginId,
                name = member.name,
                birthDate = member.birthDate,
                email = member.email,
            )
        }
    }
}
