package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class LoginId(val value: String) {

    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.")
        }
        if (!value.matches(Regex("^[a-zA-Z0-9]+$"))) {
            throw CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 사용 가능합니다.")
        }
    }
}
