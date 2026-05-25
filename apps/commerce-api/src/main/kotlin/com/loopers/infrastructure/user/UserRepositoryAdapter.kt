package com.loopers.infrastructure.user

import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class UserRepositoryAdapter(
    private val userJpaRepository: UserJpaRepository,
) : UserRepositoryPort {
    override fun findByIdOrNull(id: Long): User? =
        userJpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun save(user: User): User {
        if (user.id != 0L) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 ID가 존재하는 사용자는 저장할 수 없습니다.")
        }
        return userJpaRepository.save(UserEntity.from(user)).toDomain()
    }
}
