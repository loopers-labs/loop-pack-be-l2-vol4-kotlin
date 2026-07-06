package com.loopers.payment.application

import com.loopers.order.application.OrderFacade
import com.loopers.order.domain.OrderErrorCode
import com.loopers.order.domain.OrderRepository
import com.loopers.order.domain.OrderStatus
import com.loopers.payment.domain.CardType
import com.loopers.payment.domain.Payment
import com.loopers.payment.domain.PaymentErrorCode
import com.loopers.payment.domain.PaymentRepository
import com.loopers.payment.domain.PaymentStatus
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.ZonedDateTime

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val orderFacade: OrderFacade,
    private val alertSender: AlertSender,
) {
    @Transactional
    fun prepare(command: PaymentPrepareCommand): PreparedPayment {
        val order = orderRepository.findByOrderKey(command.orderKey)
            ?.takeIf { it.userId == command.userId }
            ?: throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            throw ConflictException(PaymentErrorCode.ORDER_NOT_PAYABLE)
        }

        val payment = paymentRepository.save(
            Payment.create(order.id, command.userId, order.totalAmount, command.cardType),
        )
        return PreparedPayment(paymentId = payment.id, amount = order.totalAmount.amount)
    }

    @Transactional
    fun reflectSubmit(paymentId: Long, result: PgSubmitResult): PaymentInfo {
        val payment = paymentRepository.findById(paymentId)
            ?: throw NotFoundException(PaymentErrorCode.PAYMENT_NOT_FOUND)
        when (result) {
            is PgSubmitResult.Accepted -> payment.accept(result.transactionKey)

            is PgSubmitResult.Rejected -> failAndCompensate(payment, result.reason)

            is PgSubmitResult.Failed -> failAndCompensate(payment, "PG 사용 불가로 결제 실패")

            is PgSubmitResult.Unknown -> {
                payment.markUnknown()
                markOrderUnknown(payment.orderId)
            }
        }
        return PaymentInfo.from(payment)
    }

    @Transactional
    fun handleCallback(command: PaymentCallbackCommand) {
        val order = orderRepository.findByOrderKey(command.orderKey)
            ?: throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)
        val payment = paymentRepository.findByOrderId(order.id)
            ?: throw NotFoundException(PaymentErrorCode.PAYMENT_NOT_FOUND)
        resolve(payment, command.status, command.transactionKey, command.reason)
    }

    @Transactional(readOnly = true)
    fun findReconcileTargets(): List<ReconcileTarget> =
        paymentRepository.findByStatus(PaymentStatus.UNKNOWN).mapNotNull { payment ->
            val order = orderRepository.findById(payment.orderId) ?: return@mapNotNull null
            ReconcileTarget(payment.id, payment.userId, order.orderKey, payment.createdAt)
        }

    @Transactional
    fun applyReconcile(paymentId: Long, queryResult: PgQueryResult): ReconcileOutcome {
        val payment = paymentRepository.findById(paymentId)
            ?: throw NotFoundException(PaymentErrorCode.PAYMENT_NOT_FOUND)
        if (isTerminal(payment.status)) {
            return ReconcileOutcome.RESOLVED
        }
        val overDeadline = Duration.between(payment.createdAt, ZonedDateTime.now()) > RECONCILE_DEADLINE
        return when (queryResult) {
            is PgQueryResult.Found -> when (queryResult.status) {
                PaymentResultStatus.SUCCESS -> {
                    resolve(payment, PaymentResultStatus.SUCCESS, queryResult.transactionKey, null)
                    ReconcileOutcome.RESOLVED
                }

                PaymentResultStatus.FAILED -> {
                    resolve(payment, PaymentResultStatus.FAILED, queryResult.transactionKey, "PG 조회 결과 실패")
                    ReconcileOutcome.RESOLVED
                }

                PaymentResultStatus.PENDING -> if (overDeadline) ReconcileOutcome.NEEDS_ALERT else ReconcileOutcome.PENDING
            }

            // PG 에 기록 없음 = 미전송. grace 지나면 실패 확정(과금 없음이므로 안전)
            is PgQueryResult.NotFound ->
                if (overDeadline) {
                    resolve(payment, PaymentResultStatus.FAILED, null, "PG 미전송 확정(deadline 초과)")
                    ReconcileOutcome.RESOLVED
                } else {
                    ReconcileOutcome.PENDING
                }

            // PG 장애로 확인 불가 — deadline 지나도 함부로 실패시키지 않고 알람
            is PgQueryResult.Unreachable -> if (overDeadline) ReconcileOutcome.NEEDS_ALERT else ReconcileOutcome.PENDING
        }
    }

    private fun resolve(payment: Payment, status: PaymentResultStatus, transactionKey: String?, reason: String?) {
        if (isTerminal(payment.status)) {
            if (payment.status == PaymentStatus.FAILED && status == PaymentResultStatus.SUCCESS) {
                alertSender.alert("결제 실패 처리된 건에 PG 성공 통보 도착 (paymentId=${payment.id}). 수동 확인 필요.")
            }
            return
        }
        when (status) {
            PaymentResultStatus.SUCCESS -> {
                if (payment.transactionKey == null && transactionKey != null) {
                    payment.accept(transactionKey)
                }
                payment.success()
                confirmOrder(payment.orderId)
            }

            PaymentResultStatus.FAILED -> failAndCompensate(payment, reason)

            PaymentResultStatus.PENDING -> Unit
        }
    }

    private fun failAndCompensate(payment: Payment, reason: String?) {
        payment.fail(reason)
        val order = orderRepository.findById(payment.orderId)
            ?: throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)
        orderFacade.cancelAndCompensate(order)
    }

    private fun confirmOrder(orderId: Long) {
        val order = orderRepository.findById(orderId)
            ?: throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)
        order.confirmPayment()
    }

    private fun markOrderUnknown(orderId: Long) {
        val order = orderRepository.findById(orderId)
            ?: throw NotFoundException(OrderErrorCode.ORDER_NOT_FOUND)
        order.markUnknown()
    }

    private fun isTerminal(status: PaymentStatus): Boolean =
        status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED

    companion object {
        private val RECONCILE_DEADLINE: Duration = Duration.ofMinutes(30)
    }
}

data class PaymentPrepareCommand(val userId: Long, val orderKey: String, val cardType: CardType)

data class PreparedPayment(val paymentId: Long, val amount: Long)

data class PaymentCallbackCommand(
    val orderKey: String,
    val transactionKey: String,
    val status: PaymentResultStatus,
    val reason: String?,
)

enum class PaymentResultStatus {
    PENDING,
    SUCCESS,
    FAILED,
}

data class ReconcileTarget(val paymentId: Long, val userId: Long, val orderKey: String, val createdAt: ZonedDateTime)

enum class ReconcileOutcome {
    RESOLVED,
    PENDING,
    NEEDS_ALERT,
}

data class PaymentInfo(val paymentId: Long, val orderId: Long, val status: PaymentStatus) {
    companion object {
        fun from(payment: Payment): PaymentInfo =
            PaymentInfo(
                paymentId = payment.id,
                orderId = payment.orderId,
                status = payment.status,
            )
    }
}
