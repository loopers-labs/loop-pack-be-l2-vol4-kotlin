package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class Password(val value: String) {
    fun matches(rawPassword: String): Boolean =
        value == PasswordEncryptionUtil.encode(rawPassword)

    companion object {
        private val PASSWORD_PATTERN = Regex("^[a-zA-Z0-9!@#\$%^&*()_+\\-=\\[\\]{}|;':\",./<>?~`\\\\]+$")
        private val BIRTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

        fun create(rawPassword: String, birth: LocalDate): Password {
            validate(rawPassword, birth)
            return Password(PasswordEncryptionUtil.encode(rawPassword))
        }

        private fun validate(rawPassword: String, birth: LocalDate) {
            if (rawPassword.length !in 8..16) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.")
            }
            if (!PASSWORD_PATTERN.matches(rawPassword)) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 사용할 수 있습니다.")
            }
            val birthString = birth.format(BIRTH_FORMAT)
            if (rawPassword.contains(birthString)) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
            }
        }
    }
}
