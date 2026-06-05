package com.loopers.domain.user.application.service

import com.loopers.domain.user.application.command.UserChangePasswordCommand
import com.loopers.domain.user.application.command.UserSignUpCommand
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
    fun findById(userId: Long): UserModel =
        userRepository.findById(userId) ?: throw CoreException(ErrorType.NOT_FOUND)

    @Transactional(readOnly = true)
    fun getMe(loginId: String, rawPassword: String): UserModel {
        val user = userRepository.findByLoginId(loginId) ?: throwUnauthorized()
        if (!user.password.matches(rawPassword, passwordEncoder)) {
            throwUnauthorized()
        }
        return user
    }

    @Transactional
    fun changePassword(command: UserChangePasswordCommand) {
        val user = userRepository.findByIdForUpdate(command.userId) ?: throwUnauthorized()
        if (!user.password.matches(command.currentRawPassword, passwordEncoder)) {
            throwUnauthorized()
        }
        if (user.password.matches(command.newRawPassword, passwordEncoder)) {
            throw CoreException(ErrorType.BAD_REQUEST, SAME_PASSWORD_MESSAGE)
        }

        try {
            val password = Password.of(command.newRawPassword, user.birthday, passwordEncoder)
            userRepository.updatePassword(user.id, password)
        } catch (e: UserDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }
    }

    private fun throwDuplicateLoginIdConflict(cause: DuplicateLoginIdException): Nothing {
        throw CoreException(ErrorType.CONFLICT, DUPLICATE_LOGIN_ID_MESSAGE, cause)
    }

    private fun throwUnauthorized(): Nothing {
        throw CoreException(ErrorType.UNAUTHORIZED)
    }

    companion object {
        private const val DUPLICATE_LOGIN_ID_MESSAGE = "이미 가입된 로그인 ID 입니다."
        private const val SAME_PASSWORD_MESSAGE = "현재 비밀번호는 새 비밀번호로 사용할 수 없습니다."
    }
}
