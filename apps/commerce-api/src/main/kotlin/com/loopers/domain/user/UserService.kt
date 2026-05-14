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
        val loginIdVo = LoginId(loginId)
        if (userRepository.findByLoginId(loginIdVo.value) != null) {
            throw CoreException(ErrorType.USER_ALREADY_EXISTS)
        }

        val birthDateVo = BirthDate(birthDate)
        val encodedPassword = Password(password, birthDateVo.value).encode(passwordEncryptor)

        val user = User(
            loginId = loginIdVo,
            password = encodedPassword,
            name = Name(name),
            birthDate = birthDateVo,
            email = Email(email),
        )
        return userRepository.save(user)
    }

    @Transactional(readOnly = true)
    fun getMyInfo(loginId: String): User {
        return userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.USER_NOT_FOUND)
    }

    @Transactional
    fun changePassword(loginId: String, newPassword: String) {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.USER_NOT_FOUND)

        Password(newPassword, user.birthDate.value)

        if (passwordEncryptor.matches(newPassword, user.password)) {
            throw CoreException(ErrorType.DUPLICATE_PASSWORD)
        }

        val encodedNewPassword = passwordEncryptor.encode(newPassword)
        userRepository.changePassword(loginId, encodedNewPassword)
    }
}
