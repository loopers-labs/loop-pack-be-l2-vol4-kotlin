package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

data class User(
    val id: Long = 0,
    val loginId: String,
    val password: Password,
    val name: String,
    val birth: LocalDate,
    val email: String,
) {
    fun isCorrectPasswd(rawPassword: String): Boolean =
        password.matches(rawPassword)

    fun changePw(prevPw: String, nextPw: String): User {
        if (!isCorrectPasswd(prevPw)) {
            throw CoreException(ErrorType.UNAUTHORIZED, "이전 비밀번호가 올바르지 않습니다.")
        }
        return copy(password = Password.create(nextPw, birth))
    }

    companion object {
        private val LOGIN_ID_PATTERN = Regex("^[a-zA-Z0-9]+$")

        fun create(
            loginId: String,
            rawPassword: String,
            name: String,
            birth: LocalDate,
            email: String,
        ): User {
            validateLoginId(loginId)
            return User(
                loginId = loginId,
                password = Password.create(rawPassword, birth),
                name = name,
                birth = birth,
                email = email,
            )
        }

        private fun validateLoginId(loginId: String) {
            if (!LOGIN_ID_PATTERN.matches(loginId)) {
                throw CoreException(ErrorType.BAD_REQUEST, "아이디는 영문과 숫자만 사용할 수 있습니다.")
            }
        }
    }
}
