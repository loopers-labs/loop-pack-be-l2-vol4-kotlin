package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun existsByLoginId(loginId: String): Boolean =
        userJpaRepository.existsByLoginId(loginId)

    override fun find(id: Long): User? =
        userJpaRepository.findByIdAndDeletedAtIsNull(id)
            ?.toDomain()

    override fun findByLoginId(loginId: String): User? =
        userJpaRepository.findByLoginIdAndDeletedAtIsNull(loginId)
            ?.toDomain()

    override fun save(user: User): User {
        val entity = user.id
            ?.let { userJpaRepository.findByIdAndDeletedAtIsNull(it) }
            ?.also { it.apply(user) }
            ?: UserJpaEntity.from(user)

        return userJpaRepository.save(entity).toDomain()
    }
}
