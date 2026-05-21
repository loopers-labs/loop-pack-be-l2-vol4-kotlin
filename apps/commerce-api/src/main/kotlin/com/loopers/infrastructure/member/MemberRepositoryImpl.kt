package com.loopers.infrastructure.member

import com.loopers.domain.member.Member
import com.loopers.domain.member.MemberRepository
import org.springframework.stereotype.Component

@Component
class MemberRepositoryImpl(
    private val memberJpaRepository: MemberJpaRepository,
) : MemberRepository {
    override fun existsByLoginId(loginId: String): Boolean {
        return memberJpaRepository.existsByLoginId(loginId)
    }

    override fun findByLoginId(loginId: String): Member? {
        return memberJpaRepository.findByLoginId(loginId)
    }

    override fun save(member: Member): Member {
        return memberJpaRepository.save(member)
    }
}
