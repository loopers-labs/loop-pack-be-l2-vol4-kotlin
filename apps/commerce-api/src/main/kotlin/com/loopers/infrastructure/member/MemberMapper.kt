package com.loopers.infrastructure.member

import com.loopers.domain.user.User

object MemberMapper {
    fun toDomain(member: MemberEntity): User {
        return User(
            id = member.id,
            loginId = member.loginId,
            password = member.password,
            name = member.name,
            birthDate = member.birthDate,
            email = member.email,
        )
    }

    fun toEntity(user: User): MemberEntity {
        return MemberEntity(
            loginId = user.loginId,
            password = user.password,
            name = user.name,
            birthDate = user.birthDate,
            email = user.email,
        )
    }
}
