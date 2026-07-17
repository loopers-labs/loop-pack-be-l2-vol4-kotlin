package com.loopers.support.error

import org.springframework.http.HttpStatus

enum class ErrorType(val status: HttpStatus, val code: String, val message: String) {
    /** 범용 에러 */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase, "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, HttpStatus.BAD_REQUEST.reasonPhrase, "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.reasonPhrase, "인증이 필요합니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.reasonPhrase, "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, HttpStatus.CONFLICT.reasonPhrase, "이미 존재하는 리소스입니다."),
    TOO_MANY_REQUESTS(
        HttpStatus.TOO_MANY_REQUESTS,
        HttpStatus.TOO_MANY_REQUESTS.reasonPhrase,
        "대기열 입장 토큰이 유효하지 않습니다. 순번을 기다려주세요.",
    ),
}
