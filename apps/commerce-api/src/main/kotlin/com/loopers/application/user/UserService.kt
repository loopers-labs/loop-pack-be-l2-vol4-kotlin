package com.loopers.application.user

import com.loopers.domain.user.User
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class UserService(
    private val userRepository: UserRepository,
) {
    fun signup(user: User) {
        if (existsByLoginId(user.loginId)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 존재하는 아이디입니다.")
        }
        userRepository.save(user)
    }

    private fun existsByLoginId(loginId: String): Boolean =
        try {
            userRepository.findByLoginId(loginId)
            true
        } catch (e: CoreException) {
            if (e.errorType == ErrorType.NOT_FOUND) false else throw e
        }
}
