package com.loopers.infrastructure.auth

import com.loopers.domain.auth.Auth
import com.loopers.domain.auth.AuthRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class AuthRepositoryAdapter(
    private val authJpaRepository: AuthJpaRepository,
) : AuthRepositoryPort {
    override fun findByLoginId(loginId: String): Auth? =
        authJpaRepository.findByLoginId(loginId)?.toDomain()

    override fun findByUserId(userId: Long): Auth? =
        authJpaRepository.findByUserId(userId)?.toDomain()

    override fun findAllByUserIdIn(userIds: Collection<Long>): List<Auth> {
        if (userIds.isEmpty()) return emptyList()
        return authJpaRepository.findAllByUserIdIn(userIds).map { it.toDomain() }
    }

    override fun existsByLoginId(loginId: String): Boolean =
        authJpaRepository.existsByLoginId(loginId)

    override fun save(auth: Auth): Auth {
        if (auth.id == 0L) {
            val entity = AuthEntity.from(auth)
            return authJpaRepository.save(entity).toDomain()
        }
        val existing = authJpaRepository.findById(auth.id).orElseThrow {
            CoreException(ErrorType.NOT_FOUND, "Auth를 찾을 수 없습니다.")
        }
        existing.changePassword(auth.password.value)
        return existing.toDomain()
    }
}
