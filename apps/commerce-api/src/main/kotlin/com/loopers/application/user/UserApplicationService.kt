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
    ): UserInfo {
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
        ).let { UserInfo.from(it) }
    }

    fun getUser(id: Long): UserInfo {
        return userRepository.find(id)?.let { UserInfo.from(it) }
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 유저입니다.")
    }

    fun getUserInfo(loginId: String, rawPassword: String): UserInfo {
        val user = findUserByLoginId(loginId)
        if (!userPasswordEncoder.matches(rawPassword, user.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED)
        }
        return UserInfo.from(user)
    }

    @Transactional
    fun changePassword(loginId: String, currentRawPassword: String, newRawPassword: String) {
        val user = findUserByLoginId(loginId)
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

    private fun findUserByLoginId(loginId: String): User {
        return userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.UNAUTHORIZED)
    }
}
