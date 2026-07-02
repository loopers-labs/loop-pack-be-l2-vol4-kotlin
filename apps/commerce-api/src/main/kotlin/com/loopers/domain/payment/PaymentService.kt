package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
@Transactional(readOnly = true)
class PaymentService(
    private val paymentRepository: PaymentRepository,
) {
    /**
     * 주문에 대한 결제를 PENDING 상태로 준비한다.
     * 이미 결제가 있으면 상태에 따라 재사용한다.
     * - SUCCESS: 충돌 (이미 결제 완료)
     * - PENDING: 진행 중인 결제를 그대로 반환 (재요청)
     * - FAILED: reopen 후 재시도
     */
    @Transactional
    fun prepare(
        orderId: Long,
        userId: Long,
        cardType: CardType,
        cardNo: String,
        amount: Long,
    ): PaymentModel {
        val existing = paymentRepository.findByOrderId(orderId)
        if (existing == null) {
            val payment = PaymentModel.create(orderId, userId, cardType, cardNo, amount)
            return paymentRepository.save(payment)
        }

        return when (existing.status) {
            PaymentStatus.SUCCESS -> throw CoreException(ErrorType.CONFLICT, "이미 결제가 완료된 주문입니다.")
            PaymentStatus.PENDING -> existing
            PaymentStatus.FAILED -> {
                existing.reopen()
                paymentRepository.save(existing)
            }
        }
    }

    @Transactional
    fun assignTransactionKey(orderId: Long, transactionKey: String): PaymentModel {
        val payment = getByOrderId(orderId)
        payment.assignTransactionKey(transactionKey)
        return paymentRepository.save(payment)
    }

    @Transactional
    fun markSuccess(transactionKey: String): PaymentModel {
        val payment = getByTransactionKey(transactionKey)
        payment.markSuccess()
        return paymentRepository.save(payment)
    }

    @Transactional
    fun markFailed(transactionKey: String, reason: String?): PaymentModel {
        val payment = getByTransactionKey(transactionKey)
        payment.markFailed(reason)
        return paymentRepository.save(payment)
    }

    fun getByOrderId(orderId: Long): PaymentModel =
        paymentRepository.findByOrderId(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다.")

    fun getByTransactionKey(transactionKey: String): PaymentModel =
        paymentRepository.findByTransactionKey(transactionKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다.")

    fun findByOrderId(orderId: Long): PaymentModel? = paymentRepository.findByOrderId(orderId)

    fun findStalePending(threshold: ZonedDateTime): List<PaymentModel> =
        paymentRepository.findByStatusAndCreatedBefore(PaymentStatus.PENDING, threshold)
}
