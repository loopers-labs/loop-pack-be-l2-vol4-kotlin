package com.loopers.infrastructure.member

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class MemberRepositoryImpl(
    private val memberJpaRepository: MemberJpaRepository,
) : UserRepository {
    override fun existsByLoginId(loginId: String): Boolean {
        return memberJpaRepository.existsByLoginId(loginId)
    }

    override fun findByLoginId(loginId: String): User? {
        return memberJpaRepository.findByLoginId(loginId)
            ?.let(MemberMapper::toDomain)
    }

    override fun save(user: User): User {
        val member = if (user.id == 0L) {
            MemberMapper.toEntity(user)
        } else {
            memberJpaRepository.findById(user.id)
                .orElseThrow { CoreException(ErrorType.NOT_FOUND, "User not found.") }
                .also { it.update(user) }
        }

        return memberJpaRepository.save(member)
            .let(MemberMapper::toDomain)
    }
}
