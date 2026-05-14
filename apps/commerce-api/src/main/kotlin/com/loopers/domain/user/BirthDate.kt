package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class BirthDate(val value: String) {

    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 필수입니다.")
        }
        if (!value.matches(Regex("^\\d{4}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])$"))) {
            throw CoreException(ErrorType.BAD_REQUEST, "생년월일은 yyyyMMdd 형식이어야 합니다.")
        }
    }
}
