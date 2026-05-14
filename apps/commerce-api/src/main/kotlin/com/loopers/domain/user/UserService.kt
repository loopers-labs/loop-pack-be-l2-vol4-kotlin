package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun register(loginId: String, rawPassword: String, name: String, birthDate: String, email: String): UserModel {
        validatePassword(rawPassword, birthDate)

        if (userRepository.findByLoginId(loginId) != null) {
            throw CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID입니다.")
        }

        return userRepository.save(
            UserModel(
                loginId = loginId,
                encodedPassword = passwordEncoder.encode(rawPassword),
                name = name,
                birthDate = birthDate,
                email = email,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun getByLoginId(loginId: String): UserModel {
        return userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 유저입니다.")
    }

    @Transactional(readOnly = true)
    fun authenticate(loginId: String, rawPassword: String): UserModel {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 유저입니다.")
        if (!passwordEncoder.matches(rawPassword, user.encodedPassword)) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.")
        }
        return user
    }

    @Transactional
    fun changePassword(userId: Long, currentRawPassword: String, newRawPassword: String): UserModel {
        val user = userRepository.findById(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 유저입니다.")

        if (!passwordEncoder.matches(currentRawPassword, user.encodedPassword)) {
            throw CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.")
        }
        if (passwordEncoder.matches(newRawPassword, user.encodedPassword)) {
            throw CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 현재 비밀번호와 달라야 합니다.")
        }
        validatePassword(newRawPassword, user.birthDate)

        user.changePassword(passwordEncoder.encode(newRawPassword))
        return userRepository.save(user)
    }

    private fun validatePassword(rawPassword: String, birthDate: String) {
        UserModel.validateRawPassword(rawPassword, birthDate)
    }
}