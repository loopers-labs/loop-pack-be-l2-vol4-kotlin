package com.loopers.domain.user.service

import com.loopers.application.user.dto.UserSignUpCommand
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.PasswordPolicy
import com.loopers.domain.user.model.User
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class UserAccountService {
    fun signUp(
        command: UserSignUpCommand,
        loginIdTaken: Boolean,
    ): User {
        if (loginIdTaken) {
            throw CoreException(ErrorType.CONFLICT, "LoginId already exists.")
        }

        PasswordPolicy.validate(
            rawPassword = command.rawPassword,
            birthDate = command.birthDate,
        )

        return User(
            loginId = command.loginId,
            password = PasswordEncoder.encode(command.rawPassword),
            name = command.name,
            birthDate = command.birthDate,
            email = command.email,
        )
    }

    fun authenticate(
        user: User,
        rawPassword: String,
    ): User {
        ensureCredentialsMatch(user, rawPassword)
        return user
    }

    fun updatePassword(
        user: User,
        rawPassword: String,
        newRawPassword: String,
    ) {
        ensureCredentialsMatch(user, rawPassword)
        if (PasswordEncoder.matches(newRawPassword, user.password)) {
            throw CoreException(ErrorType.BAD_REQUEST, "New password must be different from current password.")
        }

        PasswordPolicy.validate(
            rawPassword = newRawPassword,
            birthDate = user.birthDate,
        )

        user.updatePassword(PasswordEncoder.encode(newRawPassword))
    }

    private fun ensureCredentialsMatch(
        user: User,
        rawPassword: String,
    ) {
        if (!PasswordEncoder.matches(rawPassword, user.password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "User credentials do not match.")
        }
    }
}
