package com.loopers.application.user

import com.loopers.domain.user.EncodedPassword
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserPasswordEncoder
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Transactional(readOnly = true)
@Component
class UserApplicationService(
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
    ): User {
        if (userRepository.existsByLoginId(loginId)) {
            throw CoreException(ErrorType.CONFLICT, "이미 사용 중인 로그인 ID 입니다.")
        }
        val encodedPassword = EncodedPassword(
            userPasswordEncoder.encode(RawPassword(rawPassword, birthDate).value),
        )
        return userRepository.save(
            User(
                loginId = loginId,
                encodedPassword = encodedPassword,
                name = name,
                birthDate = birthDate,
                email = email,
            ),
        )
    }

    fun getUser(id: Long): User {
        return userRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 유저입니다.")
    }

    fun getUserInfo(loginId: String, rawPassword: String): User {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.UNAUTHORIZED)
        if (!userPasswordEncoder.matches(rawPassword, user.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }
        return user
    }

    @Transactional
    fun changePassword(loginId: String, currentRawPassword: String, newRawPassword: String) {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.UNAUTHORIZED)
        if (!userPasswordEncoder.matches(currentRawPassword, user.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }
        if (userPasswordEncoder.matches(newRawPassword, user.password)) {
            throw CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다.")
        }
        val newEncoded = EncodedPassword(
            userPasswordEncoder.encode(RawPassword(newRawPassword, user.birthDate).value),
        )
        user.changePassword(newEncoded)
        userRepository.save(user)
    }
}
