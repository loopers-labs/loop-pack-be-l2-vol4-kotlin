package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class UserModel(
    val id: Long = 0,
    val loginId: String,
    encodedPassword: String,
    val name: String,
    val birthDate: String,
    val email: String,
) {
    var encodedPassword: String = encodedPassword
        private set

    companion object {
        private val LOGIN_ID_REGEX = Regex("^[a-zA-Z0-9]{4,20}$")
        private val NAME_REGEX = Regex("^[가-힣a-zA-Z ]{1,50}$")
        private val EMAIL_REGEX = Regex("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")
        private val BIRTH_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val PASSWORD_REGEX = Regex("""^[!-~]{8,16}$""")

        fun validateRawPassword(rawPassword: String, birthDate: String) {
            if (!PASSWORD_REGEX.matches(rawPassword)) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 사용할 수 있습니다.")
            }
            if (rawPassword.contains(birthDate)) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
            }
        }
    }

    init {
        if (loginId.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 비어있을 수 없습니다.")
        if (!LOGIN_ID_REGEX.matches(loginId)) throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문 대소문자와 숫자만 사용할 수 있습니다. (4~20자)")
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.")
        if (!NAME_REGEX.matches(name)) throw CoreException(ErrorType.BAD_REQUEST, "이름은 한글 또는 영문만 사용할 수 있습니다.")
        if (birthDate.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.")
        validateBirthDate(birthDate)
        if (email.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "이메일은 비어있을 수 없습니다.")
        if (!EMAIL_REGEX.matches(email)) throw CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.")
        if (encodedPassword.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 비어있을 수 없습니다.")
    }

    fun maskedName(): String = name.dropLast(1) + "*"

    fun changePassword(newEncodedPassword: String) {
        encodedPassword = newEncodedPassword
    }

    private fun validateBirthDate(value: String) {
        if (!value.matches(Regex("^\\d{8}$"))) {
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyyMMdd 형식이어야 합니다.")
        }
        try {
            LocalDate.parse(value, BIRTH_DATE_FORMATTER)
        } catch (e: DateTimeParseException) {
            throw CoreException(ErrorType.BAD_REQUEST, "존재하지 않는 생년월일입니다.")
        }
    }
}
