package com.loopers.domain.user.infrastructure.persistence

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.model.UserModel
import com.loopers.domain.user.vo.Birthday
import com.loopers.domain.user.vo.Email
import com.loopers.domain.user.vo.LoginId
import com.loopers.domain.user.vo.Name
import com.loopers.domain.user.vo.Password
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

const val USER_LOGIN_ID_UNIQUE_CONSTRAINT = "uk_users_login_id"

@Entity
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(
            name = USER_LOGIN_ID_UNIQUE_CONSTRAINT,
            columnNames = ["login_id"],
        ),
    ],
)
class UserJpaEntity(
    @Column(name = "login_id", nullable = false)
    var loginId: String,
    @Column(name = "encoded_password", nullable = false)
    var encodedPassword: String,
    @Column(nullable = false)
    var name: String,
    @Column(nullable = false)
    var birthday: LocalDate,
    @Column(nullable = false)
    var email: String,
) : BaseEntity() {
    fun toDomain(): UserModel = UserModel(
        id = id,
        loginId = LoginId.of(loginId),
        password = Password.fromEncoded(encodedPassword),
        name = Name.of(name),
        birthday = Birthday.of(birthday),
        email = Email.of(email),
    )

    companion object {
        fun fromDomain(user: UserModel): UserJpaEntity = UserJpaEntity(
            loginId = user.loginId.value,
            encodedPassword = user.password.encodedForPersistence(),
            name = user.name.value,
            birthday = user.birthday.value,
            email = user.email.value,
        )
    }
}
