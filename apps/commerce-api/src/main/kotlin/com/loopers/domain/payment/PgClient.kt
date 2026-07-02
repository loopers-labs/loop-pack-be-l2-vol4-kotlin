package com.loopers.domain.payment

/**
 * 외부 PG 시스템에 대한 도메인 포트.
 * 인프라(Feign) 구현체가 Timeout / CircuitBreaker / Retry / Fallback 을 적용한다.
 */
interface PgClient {
    /**
     * 결제를 요청(접수)한다. 성공 시 거래 키와 함께 PENDING 상태를 받는다.
     * 외부 장애로 접수 자체가 불가하면 [PgUnavailableException] 을 던진다.
     */
    fun requestPayment(command: PgPaymentCommand): PgTransactionResult

    /**
     * 거래 키로 단건 결제 상태를 조회한다. 없으면 null.
     */
    fun getTransaction(userId: String, transactionKey: String): PgTransactionResult?

    /**
     * 주문 ID로 PG에 접수된 거래 목록을 조회한다. (타임아웃/콜백 유실 복구용)
     */
    fun findTransactionsByOrderId(userId: String, orderId: String): List<PgTransactionResult>
}

data class PgPaymentCommand(
    val userId: String,
    val orderId: String,
    val cardType: CardType,
    val cardNo: String,
    val amount: Long,
    val callbackUrl: String,
)

data class PgTransactionResult(
    val transactionKey: String,
    val orderId: String?,
    val status: PgTransactionStatus,
    val reason: String?,
)

enum class PgTransactionStatus {
    PENDING,
    SUCCESS,
    FAILED,
}
