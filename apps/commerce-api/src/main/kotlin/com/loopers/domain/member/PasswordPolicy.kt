package com.loopers.domain.member

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

object PasswordPolicy {
    fun validate(rawPassword: String) {
        if (rawPassword.length !in 8..16) {
            throw CoreException(ErrorType.BAD_REQUEST, "Password length must be between 8 and 16")
        }
    }
}
