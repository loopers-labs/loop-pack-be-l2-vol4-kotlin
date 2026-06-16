package com.loopers.infrastructure.member.mapper

import com.loopers.domain.user.model.User
import com.loopers.infrastructure.member.entity.MemberEntity

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
