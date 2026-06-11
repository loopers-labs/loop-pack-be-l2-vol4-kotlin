package com.loopers.account.domain

import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.Email
import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

@Entity
@Table(
    name = "account",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_account_email",
            columnNames = ["email"],
        ),
    ],
)
class Account(
    name: AccountName,
    birthDate: LocalDate,
    email: Email,
    role: AccountRole = AccountRole.USER,
) : BaseEntity() {
    @Embedded
    var name: AccountName = name
        private set

    @Column(name = "birth_date", nullable = false)
    var birthDate: LocalDate = birthDate
        private set

    @Embedded
    var email: Email = email
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: AccountRole = role
        private set

    fun maskedName(): String =
        name.masked()
}

enum class AccountRole {
    USER,
    ADMIN,
}
