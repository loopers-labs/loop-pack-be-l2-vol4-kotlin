package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncryptor: PasswordEncryptor,
) {
    @Transactional
    fun register(loginId: String, password: String, name: String, birthDate: String, email: String): User {
        if (userRepository.findByLoginId(loginId) != null) {
            throw CoreException(ErrorType.CONFLICT, "이미 가입된 로그인 ID 입니다.")
        }

        val encodedPassword = Password(password, birthDate).encode(passwordEncryptor)

        val user = User(
            loginId = loginId,
            password = encodedPassword,
            name = name,
            birthDate = birthDate,
            email = email,
        )
        return userRepository.save(user)
    }

    @Transactional(readOnly = true)
    fun getMyInfo(loginId: String): User {
        return userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다.")
    }

    @Transactional
    fun changePassword(loginId: String, newPassword: String) {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 사용자입니다.")

        Password(newPassword, user.birthDate)

        if (passwordEncryptor.matches(newPassword, user.password)) {
            throw CoreException(ErrorType.CONFLICT, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.")
        }

        val encodedNewPassword = passwordEncryptor.encode(newPassword)
        userRepository.changePassword(loginId, encodedNewPassword)
    }
}
