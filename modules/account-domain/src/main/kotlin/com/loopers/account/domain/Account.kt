package com.loopers.account.domain

import com.loopers.domain.BaseEntity
import com.loopers.account.domain.vo.AccountName
import com.loopers.account.domain.vo.Email
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
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

    fun maskedName(): String =
        name.masked()
}
