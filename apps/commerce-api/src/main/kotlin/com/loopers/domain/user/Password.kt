package com.loopers.domain.user

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType

class Password(private val raw: String, birthDate: String) {

    init {
        if (raw.isBlank())
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다.")
        if (!raw.matches(Regex("^[a-zA-Z0-9!@#\$%^&*()_+\\-=\\[\\]{};':\",./<>?]{8,16}$")))
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자의 영문 대소문자, 숫자, 특수문자만 가능합니다.")
        if (raw.contains(birthDate))
            throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
    }

    fun encode(encryptor: PasswordEncryptor): String = encryptor.encode(raw)
}