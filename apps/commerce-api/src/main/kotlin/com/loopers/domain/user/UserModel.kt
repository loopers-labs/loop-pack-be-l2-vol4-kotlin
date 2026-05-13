package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import java.time.LocalDate

@Entity
class UserModel(
    loginId: String,
    val password: String,
    name: String,
    val birthDate: LocalDate,
    val email: String,
) : BaseEntity() {
    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    val loginId: String = loginId

    @Column(name = "name", nullable = false, length = 20)
    var name: String = name
        protected set

    init {
        validateLoginId(loginId)
        validateName(name)
    }

    companion object {
        private val LOGIN_ID_REGEX = Regex("^[a-z0-9]{3,20}$")

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
    }
}
