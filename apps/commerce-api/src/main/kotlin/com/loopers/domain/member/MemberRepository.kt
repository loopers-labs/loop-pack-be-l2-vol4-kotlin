package com.loopers.domain.member

interface MemberRepository {
    fun existsByLoginId(loginId: String): Boolean

    fun save(member: Member): Member
}
