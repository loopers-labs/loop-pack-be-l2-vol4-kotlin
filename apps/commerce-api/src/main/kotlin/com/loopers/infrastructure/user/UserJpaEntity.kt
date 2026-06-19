package com.loopers.infrastructure.user

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.EncodedPassword
import com.loopers.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "users")
class UserJpaEntity(
    loginId: String,
    encodedPassword: EncodedPassword,
    name: String,
    birthDate: LocalDate,
    email: String,
) : BaseEntity() {
    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    val loginId: String = loginId

    @Column(name = "password", nullable = false)
    var password: String = encodedPassword.value
        protected set

    @Column(name = "name", nullable = false, length = 20)
    var name: String = name
        protected set

    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate = birthDate
        protected set

    @Column(name = "email", nullable = false)
    var email: String = email
        protected set

    fun updateFrom(user: User) {
        password = user.password
        name = user.name
        birthDate = user.birthDate
        email = user.email
    }

    fun toDomain(): User {
        return User(
            id = id,
            loginId = loginId,
            encodedPassword = EncodedPassword(password),
            name = name,
            birthDate = birthDate,
            email = email,
        )
    }

    companion object {
        fun from(user: User): UserJpaEntity {
            return UserJpaEntity(
                loginId = user.loginId,
                encodedPassword = EncodedPassword(user.password),
                name = user.name,
                birthDate = user.birthDate,
                email = user.email,
            )
        }
    }
}
