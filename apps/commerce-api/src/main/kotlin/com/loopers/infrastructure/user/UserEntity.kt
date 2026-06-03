package com.loopers.infrastructure.user

import com.loopers.persistence.BaseEntity
import com.loopers.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "user")
class UserEntity(
    @Column(name = "name", nullable = false)
    val name: String,

    @Column(name = "birth", nullable = false)
    val birth: LocalDate,

    @Column(name = "email", nullable = false)
    val email: String,
) : BaseEntity() {
    fun toDomain(): User =
        User(id = id, name = name, birth = birth, email = email)

    companion object {
        fun from(user: User): UserEntity =
            UserEntity(name = user.name, birth = user.birth, email = user.email)
    }
}
