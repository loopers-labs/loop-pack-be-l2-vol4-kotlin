package com.loopers.application.user

import com.loopers.domain.auth.Auth
import com.loopers.domain.auth.AuthRepositoryPort
import com.loopers.domain.auth.AuthService
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserFacade(
    private val userRepositoryPort: UserRepositoryPort,
    private val authRepositoryPort: AuthRepositoryPort,
    private val authService: AuthService,
) {
    @Transactional
    fun signup(command: SignupCommand): User {
        if (authRepositoryPort.existsByLoginId(command.loginId)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 존재하는 아이디입니다.")
        }
        val user = userRepositoryPort.save(
            User.create(name = command.name, birth = command.birth, email = command.email),
        )
        authRepositoryPort.save(
            Auth.create(
                userId = user.id,
                loginId = command.loginId,
                rawPassword = command.rawPassword,
                birth = user.birth,
            ),
        )
        return user
    }

    @Transactional(readOnly = true)
    fun getMyInfo(loginId: String, rawPassword: String): UserInfo {
        val userId = authService.login(loginId, rawPassword)
        val user = userRepositoryPort.findByIdOrNull(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        return UserInfo(loginId = loginId, name = user.name, birth = user.birth, email = user.email)
    }

    @Transactional
    fun changePassword(command: ChangePwCommand) {
        val userId = authService.login(command.loginId, command.loginPw)
        val user = userRepositoryPort.findByIdOrNull(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다.")
        val auth = authRepositoryPort.findByUserIdOrNull(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Auth를 찾을 수 없습니다.")
        val updated = auth.changePassword(command.prevPw, command.nextPw, user.birth)
        authRepositoryPort.save(updated)
    }
}
