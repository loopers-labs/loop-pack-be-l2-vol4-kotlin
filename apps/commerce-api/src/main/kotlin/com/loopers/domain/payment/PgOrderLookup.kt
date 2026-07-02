package com.loopers.domain.payment

sealed interface PgOrderLookup {
    data class Found(val result: PgPaymentResult) : PgOrderLookup // PG에 결제건 존재(상태 포함)
    data object NotAccepted : PgOrderLookup // PG가 정상 응답했고 접수 기록 없음(미접수 확정)
    data object Unknown : PgOrderLookup // 조회 실패/서킷 OPEN(불명, 재시도 대상)
}
