package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun save(user: User): User {
        return userJpaRepository.save(UserModel.from(user)).toDomain()
    }

    override fun findByLoginId(loginId: String): User? {
        return userJpaRepository.findByLoginId(loginId)?.toDomain()
    }
}
