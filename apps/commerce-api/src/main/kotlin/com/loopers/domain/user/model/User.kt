package com.loopers.domain.user.model

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.LocalDate

class User(
    val id: Long = 0L,
    loginId: String,
    password: String,
    name: String,
    birthDate: LocalDate,
    email: String,
) {
    var loginId: String = loginId
        private set

    var password: String = password
        private set

    var name: String = name
        private set

    var birthDate: LocalDate = birthDate
        private set

    var email: String = email
        private set

    init {
        if (!loginId.matches(LOGIN_ID_REGEX)) {
            throw CoreException(ErrorType.BAD_REQUEST, "LoginId must contain only letters and numbers.")
        }
        if (!name.matches(NAME_REGEX)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Name cannot contain special characters or numbers.")
        }
        if (!email.matches(EMAIL_REGEX)) {
            throw CoreException(ErrorType.BAD_REQUEST, "Email cannot contain special characters or numbers.")
        }
        if (birthDate.isAfter(LocalDate.now())) {
            throw CoreException(ErrorType.BAD_REQUEST, "Birthdate must be before now.")
        }
    }

    fun updatePassword(encodedPassword: String) {
        this.password = encodedPassword
    }

    companion object {
        private val LOGIN_ID_REGEX = Regex("^[A-Za-z0-9]+$")
        private val NAME_REGEX = Regex("^[A-Za-z가-힣]+$")
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    }
}
