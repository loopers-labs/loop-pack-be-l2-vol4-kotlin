package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

class User(
    val id: Long? = null,
    val loginId: String,
    encodedPassword: EncodedPassword,
    name: String,
    birthDate: LocalDate,
    email: String,
) {
    var password: String = encodedPassword.value
        private set

    var name: String = name
        private set

    var birthDate: LocalDate = birthDate
        private set

    var email: String = email
        private set

    init {
        validateLoginId(loginId)
        validateName(name)
        validateBirthDate(birthDate)
        validateEmail(email)
    }

    fun changePassword(encodedPassword: EncodedPassword) {
        password = encodedPassword.value
    }

    companion object {
        private val LOGIN_ID_REGEX = Regex("^[a-z0-9]{3,20}$")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val MIN_BIRTH_DATE: LocalDate = LocalDate.of(1900, 1, 1)

        private fun validateLoginId(loginId: String) {
            if (!LOGIN_ID_REGEX.matches(loginId)) {
                throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 3~20자 사이의 영문 소문자와 숫자로 이루어져야 합니다.")
            }
        }

        private fun validateName(name: String) {
            if (name.isEmpty() || name.length > 20) {
                throw CoreException(ErrorType.BAD_REQUEST, "이름은 1~20자여야 합니다.")
            }
        }

        private fun validateBirthDate(birthDate: LocalDate) {
            if (birthDate.isBefore(MIN_BIRTH_DATE) || !birthDate.isBefore(LocalDate.now())) {
                throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 1900-01-01 이후, 가입 날짜 이전이어야 합니다.")
            }
        }

        private fun validateEmail(email: String) {
            if (!EMAIL_REGEX.matches(email)) {
                throw CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.")
            }
        }
    }
}
