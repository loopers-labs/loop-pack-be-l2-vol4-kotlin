package com.loopers.domain.user.application.service

import com.loopers.domain.user.application.command.UserChangePasswordCommand
import com.loopers.domain.user.application.command.UserSignUpCommand
import com.loopers.domain.user.constant.UserErrorMessages
import com.loopers.domain.user.exception.DuplicateLoginIdException
import com.loopers.domain.user.exception.UserDomainException
import com.loopers.domain.user.model.UserModel
import com.loopers.domain.user.port.PasswordEncoder
import com.loopers.domain.user.port.UserRepository
import com.loopers.domain.user.vo.Birthday
import com.loopers.domain.user.vo.Email
import com.loopers.domain.user.vo.LoginId
import com.loopers.domain.user.vo.Name
import com.loopers.domain.user.vo.Password
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    fun signUp(command: UserSignUpCommand): UserModel {
        return try {
            if (userRepository.existsByLoginId(command.loginId)) {
                throwDuplicateLoginIdConflict(DuplicateLoginIdException(command.loginId))
            }
            val birthday = Birthday.of(command.birthday)
            val password = Password.of(command.rawPassword, birthday, passwordEncoder)
            val user = UserModel(
                loginId = LoginId.of(command.loginId),
                password = password,
                name = Name.of(command.name),
                birthday = birthday,
                email = Email.of(command.email),
            )
            try {
                userRepository.save(user)
            } catch (e: DuplicateLoginIdException) {
                throwDuplicateLoginIdConflict(e)
            }
        } catch (e: UserDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
    }

    @Transactional(readOnly = true)
    fun getById(userId: Long): UserModel =
        userRepository.findByIdOrNull(userId) ?: throw CoreException(ErrorType.NOT_FOUND)

    @Transactional(readOnly = true)
    fun getMe(loginId: String, rawPassword: String): UserModel {
        val user = userRepository.findByLoginIdOrNull(loginId) ?: throwUnauthorized()
        if (!user.password.matches(rawPassword, passwordEncoder)) {
            throwUnauthorized()
        }
        return user
    }

    @Transactional
    fun changePassword(command: UserChangePasswordCommand) {
        val user = userRepository.findByIdForUpdateOrNull(command.userId) ?: throwUnauthorized()
        if (!user.password.matches(command.currentRawPassword, passwordEncoder)) {
            throwUnauthorized()
        }
        if (user.password.matches(command.newRawPassword, passwordEncoder)) {
            throw CoreException(ErrorType.BAD_REQUEST, UserErrorMessages.SAME_PASSWORD)
        }

        try {
            val password = Password.of(command.newRawPassword, user.birthday, passwordEncoder)
            userRepository.updatePassword(user.id, password)
        } catch (e: UserDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
    }

    private fun throwDuplicateLoginIdConflict(cause: DuplicateLoginIdException): Nothing {
        throw CoreException(ErrorType.CONFLICT, UserErrorMessages.DUPLICATE_LOGIN_ID, cause)
    }

    private fun throwUnauthorized(): Nothing {
        throw CoreException(ErrorType.UNAUTHORIZED)
    }
}
