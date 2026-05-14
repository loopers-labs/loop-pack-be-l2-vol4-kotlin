package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Component
class UserService(
    private val userRepository: UserRepository,
    private val userPasswordEncoder: UserPasswordEncoder,
) {
    @Transactional
    fun signUp(
        loginId: String,
        rawPassword: String,
        name: String,
        birthDate: LocalDate,
        email: String,
    ): UserModel {
        if (userRepository.existsByLoginId(loginId)) {
            throw CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID 입니다.")
        }
        val encodedPassword = EncodedPassword(
            userPasswordEncoder.encode(RawPassword(rawPassword, birthDate).value),
        )
        return userRepository.save(
            UserModel(
                loginId = loginId,
                encodedPassword = encodedPassword,
                name = name,
                birthDate = birthDate,
                email = email,
            ),
        )
    }

    fun getUserInfo(loginId: String, rawPassword: String): UserModel {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.UNAUTHORIZED)
        if (!userPasswordEncoder.matches(rawPassword, user.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }
        return user
    }
}
