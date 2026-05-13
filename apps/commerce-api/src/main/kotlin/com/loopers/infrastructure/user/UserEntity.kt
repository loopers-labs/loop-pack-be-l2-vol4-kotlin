package com.loopers.infrastructure.user

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.User
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "user")
class UserEntity(
    val loginId: String,
    var password: String,
    val name: String,
    val birth: LocalDate,
    val email: String,
) : BaseEntity() {
    fun changePassword(newPassword: String) {
        this.password = newPassword
    }

    fun toDomain(): User =
        User(
            id = id,
            loginId = loginId,
            password = password,
            name = name,
            birth = birth,
            email = email,
        )

    companion object {
        fun from(user: User): UserEntity =
            UserEntity(
                loginId = user.loginId,
                password = user.password,
                name = user.name,
                birth = user.birth,
                email = user.email,
            )
    }
}
