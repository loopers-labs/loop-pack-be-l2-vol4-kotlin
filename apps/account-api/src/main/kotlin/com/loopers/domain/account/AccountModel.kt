package com.loopers.domain.account

import com.loopers.domain.BaseEntity
import com.loopers.domain.account.vo.AccountName
import com.loopers.domain.account.vo.BirthDate
import com.loopers.domain.account.vo.Email
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "account")
class AccountModel(
    name: AccountName,
    birthDate: BirthDate,
    email: Email,
) : BaseEntity() {
    @Embedded
    var name: AccountName = name
        protected set

    @Embedded
    var birthDate: BirthDate = birthDate
        protected set

    @Embedded
    var email: Email = email
        protected set

    fun maskedName(): String =
        name.masked()
}
