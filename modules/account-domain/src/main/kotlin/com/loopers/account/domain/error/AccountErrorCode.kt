package com.loopers.account.domain.error

import com.loopers.support.error.ErrorCode

enum class AccountErrorCode(
    override val message: String,
) : ErrorCode {
    ACCOUNT_NOT_FOUND("계정을 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID("이미 가입된 로그인 ID입니다."),
    INVALID_EMAIL("이메일 형식이 올바르지 않습니다."),
    INVALID_BIRTH_DATE("생년월일이 올바르지 않습니다."),
    INVALID_ACCOUNT_NAME("이름이 올바르지 않습니다."),
    INVALID_CREDENTIAL_IDENTIFIER("인증 식별자가 올바르지 않습니다."),
    INVALID_CREDENTIAL_SECRET("인증 secret이 올바르지 않습니다."),
    INVALID_PASSWORD("비밀번호 형식이 올바르지 않습니다."),
    ;

    override val code: String
        get() = "ACCOUNT:$name"
}
