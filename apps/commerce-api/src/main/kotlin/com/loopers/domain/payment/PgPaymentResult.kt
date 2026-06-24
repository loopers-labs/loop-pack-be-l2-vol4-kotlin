package com.loopers.domain.payment

data class PgPaymentResult(
    val transactionKey: String?,
    val status: PgStatus,
    val failureReason: PaymentFailureReason?,
) {
    companion object {
        // 요청 접수 실패/타임아웃/서킷 OPEN: 결과 불명 → PENDING 유지(reconciliation 대상)
        fun unknown(): PgPaymentResult = PgPaymentResult(transactionKey = null, status = PgStatus.PENDING, failureReason = null)
    }
}
