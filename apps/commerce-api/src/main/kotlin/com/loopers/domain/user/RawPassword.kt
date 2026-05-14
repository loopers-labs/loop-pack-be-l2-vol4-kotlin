package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

class RawPassword(val value: String, birthDate: LocalDate) {
    init {
        validateLength(value)
        validateCharacters(value)
        validateBirthDateTokens(value, birthDate)
    }

    companion object {
        private val ALLOWED_REGEX =
            Regex("^[A-Za-z0-9!@#\$%^&*()_+\\-=\\[\\]{}|\\\\:;'\",./<>?~`]+$")
        private const val MIN_LENGTH = 8
        private const val MAX_LENGTH = 16

        private fun validateLength(password: String) {
            if (password.length !in MIN_LENGTH..MAX_LENGTH) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.")
            }
        }

        private fun validateCharacters(password: String) {
            if (!ALLOWED_REGEX.matches(password)) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문, 숫자, 특수문자만 허용됩니다.")
            }
        }

        private fun validateBirthDateTokens(password: String, birthDate: LocalDate) {
            if (birthDateTokens(birthDate).any { it in password }) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에는 생년월일을 포함할 수 없습니다.")
            }
        }

        private fun birthDateTokens(birthDate: LocalDate): List<String> {
            val yyyy = "%04d".format(birthDate.year)
            val yy = yyyy.takeLast(2)
            val mm = "%02d".format(birthDate.monthValue)
            val dd = "%02d".format(birthDate.dayOfMonth)
            return listOf(yyyy + mm + dd, yy + mm + dd, mm + dd, yyyy)
        }
    }
}
