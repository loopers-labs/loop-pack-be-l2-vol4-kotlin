package com.loopers.application.user

import com.loopers.domain.user.UserAccountService
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.UserSignUpCommand
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserService(
    private val userRepository: UserRepository,
    private val userAccountService: UserAccountService,
) {
    @Transactional
    fun signUp(command: UserSignUpCommand): UserInfo {
        val user = userAccountService.signUp(
            command = command,
            loginIdTaken = userRepository.existsByLoginId(command.loginId),
        )

        return userRepository.save(user)
            .let { UserInfo.from(it) }
    }

    @Transactional(readOnly = true)
    fun getMe(
        loginId: String,
        rawPassword: String,
    ): UserInfo {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "User not found.")

        return userAccountService.authenticate(user, rawPassword)
            .let { UserInfo.from(it) }
    }

    @Transactional
    fun updatePassword(
        loginId: String,
        rawPassword: String,
        newRawPassword: String,
    ) {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "User not found.")

        userAccountService.updatePassword(
            user = user,
            rawPassword = rawPassword,
            newRawPassword = newRawPassword,
        )
        userRepository.save(user)
    }
}
