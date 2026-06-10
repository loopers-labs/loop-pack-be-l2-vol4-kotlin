package com.loopers.infrastructure.user

import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun save(user: UserModel): UserModel {
        return userJpaRepository.save(user)
    }

    override fun findById(id: Long): UserModel? {
        return userJpaRepository.findById(id).orElse(null)
    }

    override fun findByLoginId(loginId: String): UserModel? {
        return userJpaRepository.findByLoginId(loginId)
    }

    override fun existsByLoginId(loginId: String): Boolean {
        return userJpaRepository.existsByLoginId(loginId)
    }
}
