package com.loopers.domain.member

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "member")
class Member(
    loginId: String,
    password: String,
    name: String,
    birthDate: LocalDate,
    email: String,
) : BaseEntity() {
    @Column(nullable = false, unique = true)
    var loginId: String = loginId
        protected set

    @Column(nullable = false)
    var password: String = password
        protected set

    @Column(nullable = false)
    var name: String = name
        protected set

    @Column(nullable = false)
    var birthDate: LocalDate = birthDate
        protected set

    @Column(nullable = false)
    var email: String = email
        protected set

    init {
        if (!name.matches(NAME_REGEX)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Name cannot contain special characters or numbers.")
        }
        if (!email.matches(EMAIL_REGEX)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Email cannot contain special characters or numbers.")
        }
    }

    companion object {
        private val NAME_REGEX = Regex("^[A-Za-z가-힣]+$")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
