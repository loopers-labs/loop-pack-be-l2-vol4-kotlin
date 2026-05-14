package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "users")
class UserModel(
    loginId: String,
    password: String,
    name: String,
    birthDate: LocalDate,
    email: String,
) : BaseEntity() {
    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    val loginId: String = loginId

    @Column(name = "password", nullable = false)
    val password: String = password

    @Column(name = "name", nullable = false, length = 20)
    var name: String = name
        protected set

    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate = birthDate
        protected set

    @Column(name = "email", nullable = false)
    var email: String = email
        protected set

    init {
        validateLoginId(loginId)
        validateName(name)
        validateBirthDate(birthDate)
        validateEmail(email)
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
            if (birthDate.isBefore(MIN_BIRTH_DATE) || birthDate.isAfter(LocalDate.now())) {
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
