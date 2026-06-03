package com.loopers.domain.auth

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

data class Auth(
    val id: Long = 0L,
    val userId: Long,
    val loginId: String,
    val password: Password,
) {
    fun matches(rawPassword: String): Boolean =
        password.matches(rawPassword)

    fun changePassword(prevPw: String, nextPw: String, birth: LocalDate): Auth {
        if (!matches(prevPw)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "이전 비밀번호가 올바르지 않습니다.")
        }
        return copy(password = Password.create(nextPw, birth))
    }

    companion object {
        private val LOGIN_ID_PATTERN = Regex("^[a-zA-Z0-9]+$")

        fun create(userId: Long, loginId: String, rawPassword: String, birth: LocalDate): Auth {
            validateLoginId(loginId)
            return Auth(
                userId = userId,
                loginId = loginId,
                password = Password.create(rawPassword, birth),
            )
        }

        private fun validateLoginId(loginId: String) {
            if (!LOGIN_ID_PATTERN.matches(loginId)) {
                throw CoreException(ErrorType.BAD_REQUEST, "아이디는 영문과 숫자만 사용할 수 있습니다.")
            }
        }
    }
}
