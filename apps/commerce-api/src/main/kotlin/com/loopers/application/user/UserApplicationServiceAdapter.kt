package com.loopers.application.user

import com.loopers.domain.auth.AuthService
import com.loopers.domain.user.User
import com.loopers.domain.user.UserService
import com.loopers.interfaces.api.user.UserApplicationServicePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserApplicationServiceAdapter(
    private val userService: UserService,
    private val authService: AuthService,
) : UserApplicationServicePort {
    @Transactional
    override fun signup(command: SignupCommand): User {
        val user = userService.create(name = command.name, birth = command.birth, email = command.email)
        authService.register(
            userId = user.id,
            loginId = command.loginId,
            rawPassword = command.rawPassword,
            birth = user.birth,
        )
        return user
    }

    @Transactional(readOnly = true)
    override fun getMyInfo(loginId: String, rawPassword: String): UserInfo {
        val userId = authService.login(loginId, rawPassword)
        val user = userService.getById(userId)
        return UserInfo(loginId = loginId, name = user.name, birth = user.birth, email = user.email)
    }

    @Transactional
    override fun changePassword(command: ChangePwCommand) {
        val userId = authService.login(command.loginId, command.loginPw)
        val user = userService.getById(userId)
        authService.changePassword(userId = userId, prevPw = command.prevPw, nextPw = command.nextPw, birth = user.birth)
    }
}
