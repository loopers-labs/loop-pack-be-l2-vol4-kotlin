package com.loopers.infrastructure.auth

import com.loopers.persistence.BaseEntity
import com.loopers.domain.auth.Auth
import com.loopers.domain.auth.Password
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "auth",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_auth_user_id", columnNames = ["user_id"]),
        UniqueConstraint(name = "uk_auth_login_id", columnNames = ["login_id"]),
    ],
)
class AuthEntity(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "login_id", nullable = false, length = 50)
    val loginId: String,

    @Column(name = "password", nullable = false)
    var password: String,
) : BaseEntity() {
    fun changePassword(newPassword: String) {
        this.password = newPassword
    }

    fun toDomain(): Auth =
        Auth(
            id = id,
            userId = userId,
            loginId = loginId,
            password = Password(password),
        )

    companion object {
        fun from(auth: Auth): AuthEntity =
            AuthEntity(
                userId = auth.userId,
                loginId = auth.loginId,
                password = auth.password.value,
            )
    }
}
