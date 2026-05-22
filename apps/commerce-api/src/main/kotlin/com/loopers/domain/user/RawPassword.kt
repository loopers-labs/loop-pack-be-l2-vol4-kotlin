package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

@JvmInline
value class RawPassword(val value: String) {
    init {
        if (!value.matches(PATTERN)) {
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문/숫자/특수문자로 8자 이상 16자 이하여야 합니다.")
        }
    }

    override fun toString(): String = "***"

    companion object {
        private val PATTERN = Regex("^[A-Za-z0-9!@#\$%^&*()\\-_=+\\[\\]{};:'\",.<>/?\\\\|`~]{8,16}$")
    }
}
