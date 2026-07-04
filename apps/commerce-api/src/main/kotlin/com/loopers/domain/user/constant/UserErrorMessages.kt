package com.loopers.domain.user.constant

object UserErrorMessages {
    const val DUPLICATE_LOGIN_ID = "이미 가입된 로그인 ID 입니다."
    const val INVALID_LOGIN_ID_FORMAT = "로그인 ID는 영문/숫자 4~20자여야 합니다."
    const val INVALID_EMAIL_FORMAT = "이메일 형식이 올바르지 않습니다."
    const val BIRTHDAY_MUST_BE_PAST = "생년월일은 과거 일자여야 합니다."
    const val INVALID_PASSWORD_FORMAT =
        "비밀번호는 8~16자의 영문 대문자, 소문자, 숫자, 특수문자를 포함해야 하며 공백을 포함할 수 없습니다."
    const val PASSWORD_CONTAINS_BIRTHDAY = "비밀번호에 생년월일을 포함할 수 없습니다."
    const val SAME_PASSWORD = "현재 비밀번호는 새 비밀번호로 사용할 수 없습니다."
}
