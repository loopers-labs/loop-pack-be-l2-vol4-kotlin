package com.loopers.infrastructure.user

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.UserModel
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserEntity(
    loginId: String,
    encodedPassword: String,
    name: String,
    birthDate: String,
    email: String,
) : BaseEntity() {

    @Column(name = "login_id", nullable = false, unique = true)
    val loginId: String = loginId

    @Column(name = "password", nullable = false)
    var encodedPassword: String = encodedPassword

    @Column(name = "name", nullable = false)
    val name: String = name

    @Column(name = "birth_date", nullable = false)
    val birthDate: String = birthDate

    @Column(name = "email", nullable = false)
    val email: String = email

    fun toDomain() = UserModel(
        id = this.id,
        loginId = this.loginId,
        encodedPassword = this.encodedPassword,
        name = this.name,
        birthDate = this.birthDate,
        email = this.email,
    )

    companion object {
        fun from(model: UserModel) = UserEntity(
            loginId = model.loginId,
            encodedPassword = model.encodedPassword,
            name = model.name,
            birthDate = model.birthDate,
            email = model.email,
        )
    }
}
