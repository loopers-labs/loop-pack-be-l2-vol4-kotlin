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
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다.")

        if (!passwordEncryptor.matches(password, user.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "비밀번호가 일치하지 않습니다.")
        }

        return user
    }
}
