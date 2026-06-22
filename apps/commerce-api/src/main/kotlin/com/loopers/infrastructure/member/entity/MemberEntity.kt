package com.loopers.infrastructure.member.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.user.model.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "member")
class MemberEntity(
    @Column(nullable = false, unique = true)
    var loginId: String,

    @Column(nullable = false)
    var password: String,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var birthDate: LocalDate,

    @Column(nullable = false)
    var email: String,
) : BaseEntity() {
    fun update(user: User) {
        loginId = user.loginId
        password = user.password
        name = user.name
        birthDate = user.birthDate
        email = user.email
    }
}
