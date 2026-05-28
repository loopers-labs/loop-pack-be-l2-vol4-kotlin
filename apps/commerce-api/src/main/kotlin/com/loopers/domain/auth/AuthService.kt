package com.loopers.domain.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

class AuthService(
    private val authRepositoryPort: AuthRepositoryPort,
) {
    fun login(loginId: String, rawPassword: String): Long {
        val auth = authRepositoryPort.findByLoginId(loginId)
            ?: throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        if (!auth.matches(rawPassword)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        return auth.userId
    }

    fun register(userId: Long, loginId: String, rawPassword: String, birth: LocalDate): Auth {
        if (authRepositoryPort.existsByLoginId(loginId)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이미 존재하는 아이디입니다.")
        }
        return authRepositoryPort.save(
            Auth.create(userId = userId, loginId = loginId, rawPassword = rawPassword, birth = birth),
        )
    }

    fun changePassword(userId: Long, prevPw: String, nextPw: String, birth: LocalDate) {
        val auth = authRepositoryPort.findByUserId(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Auth를 찾을 수 없습니다.")
        val updated = auth.changePassword(prevPw, nextPw, birth)
        authRepositoryPort.save(updated)
    }

    fun findLoginIdsByUserIds(userIds: Collection<Long>): Map<Long, String> {
        if (userIds.isEmpty()) return emptyMap()
        return authRepositoryPort.findAllByUserIdIn(userIds).associate { it.userId to it.loginId }
    }
}
