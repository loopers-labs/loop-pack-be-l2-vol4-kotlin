package com.loopers.domain.user.infrastructure.persistence

import com.loopers.domain.user.exception.DuplicateLoginIdException
import com.loopers.domain.user.model.UserModel
import com.loopers.domain.user.port.UserRepository
import com.loopers.domain.user.vo.Password
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun existsByLoginId(loginId: String): Boolean = userJpaRepository.existsByLoginId(loginId)

    override fun save(user: UserModel): UserModel =
        try {
            userJpaRepository.saveAndFlush(UserJpaEntity.fromDomain(user)).toDomain()
        } catch (e: DataIntegrityViolationException) {
            if (e.isLoginIdUniqueConstraintViolation()) {
                throw DuplicateLoginIdException(user.loginId.value, e)
            }
            throw e
        }

    override fun findById(id: Long): UserModel? = userJpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun findByLoginId(loginId: String): UserModel? = userJpaRepository.findByLoginId(loginId)?.toDomain()

    override fun findByIdForUpdate(id: Long): UserModel? = userJpaRepository.findByIdForUpdate(id)?.toDomain()

    override fun updatePassword(id: Long, password: Password) {
        userJpaRepository.findById(id).orElseThrow().encodedPassword = password.encodedForPersistence()
    }

    private fun DataIntegrityViolationException.isLoginIdUniqueConstraintViolation(): Boolean =
        generateSequence(this as Throwable?) { it.cause }
            .mapNotNull { it.message }
            .any { it.contains(USER_LOGIN_ID_UNIQUE_CONSTRAINT, ignoreCase = true) }
}
