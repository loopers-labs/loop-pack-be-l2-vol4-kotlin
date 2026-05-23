package com.loopers.infrastructure.user

import com.loopers.application.user.UserRepository
import com.loopers.domain.user.User
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun findByLoginId(loginId: String): User {
        val entity = userJpaRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        return entity.toDomain()
    }

    override fun save(user: User): User {
        if (user.id != 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 ID가 존재하는 사용자는 저장할 수 없습니다.")
        }
        val entity = UserEntity.from(user)
        return userJpaRepository.save(entity).toDomain()
    }

    override fun update(user: User): User {
        val entity = userJpaRepository.findByLoginId(user.loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        entity.changePassword(user.password.value)
        return entity.toDomain()
    }
}
