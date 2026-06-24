package com.loopers.domain.payment

enum class PaymentFailureReason {
    LIMIT_EXCEEDED,
    INVALID_CARD,
    TIMEOUT_UNKNOWN,
    ;

    companion object {
        // PG가 내려주는 reason 문자열을 우리 도메인 enum 으로 해석한다.
        // ponytail: 매칭 안 되면 TIMEOUT_UNKNOWN(결과불명)으로 보수적으로 처리.
        fun fromPgReason(reason: String?): PaymentFailureReason =
            when (reason?.uppercase()?.replace("-", "_")) {
                "LIMIT_EXCEEDED" -> LIMIT_EXCEEDED
                "INVALID_CARD" -> INVALID_CARD
                else -> TIMEOUT_UNKNOWN
            }
    }
}
