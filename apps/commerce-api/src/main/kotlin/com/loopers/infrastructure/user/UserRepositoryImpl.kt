package com.loopers.infrastructure.user

import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun findById(id: Long): UserModel? =
        userJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByLoginId(loginId: String): UserModel? =
        userJpaRepository.findByLoginId(loginId)?.toDomain()

    override fun save(user: UserModel): UserModel {
        val entity = if (user.id == 0L) {
            UserEntity.from(user)
        } else {
            userJpaRepository.findById(user.id).orElseThrow()
                .apply { encodedPassword = user.encodedPassword }
        }
        return userJpaRepository.save(entity).toDomain()
    }
}
