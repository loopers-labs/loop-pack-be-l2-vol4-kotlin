package com.loopers.domain.payment

/**
 * 외부 PG 시스템에 결제 접수 자체가 불가능한 상태(타임아웃, 서킷 오픈, 반복 실패 등)를 나타낸다.
 * 이 경우 결제는 PENDING 으로 남고, 사용자에게는 "결제 대기" 로 안전하게 응답한 뒤
 * 콜백 또는 동기화로 결과를 복구한다.
 */
class PgUnavailableException(
    message: String = "현재 결제 요청을 처리할 수 없습니다. 잠시 후 다시 확인해주세요.",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
