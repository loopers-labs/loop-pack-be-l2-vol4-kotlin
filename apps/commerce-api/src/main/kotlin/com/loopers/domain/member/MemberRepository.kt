package com.loopers.domain.member

interface MemberRepository {
    fun existsByLoginId(loginId: String): Boolean

    fun findByLoginId(loginId: String): Member?

    fun save(member: Member): Member
}
