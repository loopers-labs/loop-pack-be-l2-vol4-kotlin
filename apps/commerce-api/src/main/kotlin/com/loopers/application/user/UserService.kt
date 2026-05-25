package com.loopers.application.user

import com.loopers.domain.user.User
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserService(
    private val userRepository: UserRepository,
) {
    fun login(loginId: String, password: String): User {
        val user = try {
            userRepository.findByLoginId(loginId)
        } catch (e: CoreException) {
            throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        if (!user.isCorrectPasswd(password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        return user
    }

    @Transactional
    fun changePw(command: ChangePwCommand) {
        val user = try {
            userRepository.findByLoginId(command.loginId)
        } catch (e: CoreException) {
            throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        val updatedUser = user.changePw(command.prevPw, command.nextPw)
        userRepository.update(updatedUser)
    }

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
