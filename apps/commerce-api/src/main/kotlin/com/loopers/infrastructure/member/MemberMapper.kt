package com.loopers.infrastructure.member

import com.loopers.domain.user.User

object MemberMapper {
    fun toDomain(member: Member): User {
        return User(
            id = member.id,
            loginId = member.loginId,
            password = member.password,
            name = member.name,
            birthDate = member.birthDate,
            email = member.email,
        )
    }

    fun toEntity(user: User): Member {
        return Member(
            loginId = user.loginId,
            password = user.password,
            name = user.name,
            birthDate = user.birthDate,
            email = user.email,
        )
    }
}
