package com.loopers.support.error

enum class CommonErrorCode(
    private val reason: String,
    override val message: String,
) : ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "잘못된 요청입니다."),
    UNAUTHORIZED("UNAUTHORIZED", "인증이 필요합니다."),
    FORBIDDEN("FORBIDDEN", "접근 권한이 없습니다."),
    NOT_FOUND("NOT_FOUND", "존재하지 않는 요청입니다."),
    CONFLICT("CONFLICT", "이미 존재하는 리소스입니다."),
    INTERNAL_ERROR("INTERNAL_ERROR", "일시적인 오류가 발생했습니다."),
    ;

    override val code: String
        get() = "COMMON:$reason"
}
