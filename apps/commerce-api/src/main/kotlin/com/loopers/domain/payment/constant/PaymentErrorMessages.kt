package com.loopers.domain.payment.constant

object PaymentErrorMessages {
    const val EXTERNAL_TRANSACTION_KEY_IMMUTABLE = "외부 거래 키는 변경할 수 없습니다."
    const val PAYMENT_ID_NEGATIVE = "결제 ID는 음수일 수 없습니다."
    const val STORED_PAYMENT_ID_NOT_POSITIVE = "저장된 결제 ID는 양수여야 합니다."
    const val ORDER_ID_NOT_POSITIVE = "주문 ID는 양수여야 합니다."
    const val EXTERNAL_TRANSACTION_KEY_BLANK = "외부 거래 키는 비어 있을 수 없습니다."
    const val COMPLETED_PAYMENT_REQUIRES_COMPLETED_AT = "완료 결제는 완료 시각이 필요합니다."
    const val INCOMPLETE_PAYMENT_HAS_COMPLETED_AT = "미완료 결제는 완료 시각을 가질 수 없습니다."
    const val PG_CIRCUIT_OPEN = "PG 서킷이 열려 결제 상태를 확정할 수 없습니다."
    const val PG_STATUS_UNCONFIRMED = "PG 결제 상태를 확정할 수 없습니다."
    const val PG_RESPONSE_NO_DATA = "PG 응답에 데이터가 없습니다."
    const val CALLBACK_SECRET_MISMATCH = "콜백 시크릿이 일치하지 않습니다."
    const val ORDER_NOT_PAYABLE = "결제 대기 상태 주문만 결제할 수 있습니다."
}
