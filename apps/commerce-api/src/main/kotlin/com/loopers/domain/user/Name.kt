package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Name(val value: String) {

    init {
        if (value.isBlank()) {
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 비어있을 수 없습니다.")
        }
        if (value.length < 2 || value.length > 20) {
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 2자~20자 이내입니다.")
        }
        if (!value.matches(Regex("^[가-힣a-zA-Z]+$"))) {
            throw CoreException(ErrorType.BAD_REQUEST, "이름은 한글 또는 영문만 가능합니다.")
        }
    }

    fun masked(): String = value.dropLast(1) + "*"
}
