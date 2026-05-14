package com.loopers.domain.auth

import com.loopers.domain.user.PasswordEncryptor
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncryptor: PasswordEncryptor,
) {
    fun authenticate(loginId: String, password: String): User {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.USER_NOT_FOUND)

        if (!passwordEncryptor.matches(password, user.password)) {
            throw CoreException(ErrorType.INVALID_PASSWORD)
        }

        return user
    }
}
