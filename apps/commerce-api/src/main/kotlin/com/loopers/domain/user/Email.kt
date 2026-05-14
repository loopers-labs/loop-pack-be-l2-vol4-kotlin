package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Email(val value: String) {

    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "이메일은 필수입니다.")
        }
        if (!value.matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))) {
            throw CoreException(ErrorType.BAD_REQUEST, "유효한 이메일 형식이 아닙니다.")
        }
    }
}
