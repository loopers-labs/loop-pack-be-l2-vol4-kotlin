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
class User(
    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    val loginId: String,

    @Column(name = "encrypted_password", nullable = false)
    var encryptedPassword: String,

    @Column(name = "name", nullable = false, length = 50)
    val name: String,

    @Column(name = "birthdate", nullable = false)
    val birthdate: LocalDate,

    @Column(name = "email", nullable = false, length = 100)
    val email: String,
) : BaseEntity() {
    init {
        if (!loginId.matches(LOGIN_ID_PATTERN)) {
            throw CoreException(ErrorType.BAD_REQUEST, "loginId 는 영문/숫자 4~20자여야 합니다.")
        }
        if (name.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.")
        }
        if (!email.matches(EMAIL_PATTERN)) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.")
        }
        if (!birthdate.isBefore(LocalDate.now())) {
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 과거 날짜여야 합니다.")
        }
    }

    fun changePassword(newEncryptedPassword: String) {
        this.encryptedPassword = newEncryptedPassword
    }

    companion object {
        private val LOGIN_ID_PATTERN = Regex("^[A-Za-z0-9]{4,20}$")
        private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$")
    }
}
