package com.loopers.domain.payment

data class Payment(
    val id: Long = 0L,
    val userId: Long,
    val orderId: Long,
    val transactionKey: String,
    val cardType: String,
    val cardNo: String,
    val amount: Long,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val reason: String? = null,
) {
    init {
        require(userId > 0) { "회원 ID는 0보다 커야 합니다." }
        require(orderId > 0) { "주문 ID는 0보다 커야 합니다." }
        require(transactionKey.isNotBlank()) { "트랜잭션 키는 비어 있을 수 없습니다." }
        require(cardType.isNotBlank()) { "카드 종류는 비어 있을 수 없습니다." }
        require(cardNo.isNotBlank()) { "카드 번호는 비어 있을 수 없습니다." }
        require(amount > 0) { "결제 금액은 0보다 커야 합니다." }
    }

    fun approve(): Payment {
        require(status == PaymentStatus.PENDING) { "결제 승인은 대기 상태에서만 가능합니다: $status" }
        return copy(status = PaymentStatus.SUCCESS, reason = "정상 승인되었습니다.")
    }

    fun fail(reason: String?): Payment {
        require(status == PaymentStatus.PENDING) { "결제 실패 처리는 대기 상태에서만 가능합니다: $status" }
        return copy(status = PaymentStatus.FAILED, reason = reason ?: "결제가 실패했습니다.")
    }

    companion object {
        fun create(
            userId: Long,
            orderId: Long,
            transactionKey: String,
            cardType: String,
            cardNo: String,
            amount: Long,
        ): Payment = Payment(
            userId = userId,
            orderId = orderId,
            transactionKey = transactionKey,
            cardType = cardType,
            cardNo = cardNo,
            amount = amount,
            status = PaymentStatus.PENDING,
        )
    }
}
