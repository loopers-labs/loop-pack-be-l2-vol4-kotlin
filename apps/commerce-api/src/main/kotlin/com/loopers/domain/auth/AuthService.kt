package com.loopers.domain.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class AuthService(
    private val authRepositoryPort: AuthRepositoryPort,
) {
    fun login(loginId: String, rawPassword: String): Long {
        val auth = authRepositoryPort.findByLoginIdOrNull(loginId)
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        if (!auth.matches(rawPassword)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        return auth.userId
    }
}
