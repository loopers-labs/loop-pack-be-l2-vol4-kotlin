package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "users")
class UserModel(
    loginId: String,
    password: String,
    name: String,
    birthDate: String,
    email: String,
) : BaseEntity() {

    val loginId: String = loginId
    val birthDate: String = birthDate
    val email: String = email

    var name: String = name
        protected set

    var password: String = password
        protected set

    fun changePassword(newEncodedPassword: String) {
        password = newEncodedPassword
    }

    fun toDomain(): User = User(
        id = this.id,
        loginId = this.loginId,
        password = this.password,
        name = this.name,
        birthDate = this.birthDate,
        email = this.email,
    )

    companion object {
        fun from(user: User): UserModel = UserModel(
            loginId = user.loginId,
            password = user.password,
            name = user.name,
            birthDate = user.birthDate,
            email = user.email,
        )
    }
}
