package com.loopers.application.payment

import com.loopers.application.coupon.CouponService
import com.loopers.application.inventory.InventoryService
import com.loopers.application.payment.dto.PaymentCommand
import com.loopers.application.payment.dto.PaymentInfo
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.model.Order
import com.loopers.domain.order.repository.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PaymentService(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val inventoryService: InventoryService,
    private val couponService: CouponService,
) {
    @Transactional(readOnly = true)
    fun findByIdempotencyKey(memberId: Long, idempotencyKey: String): PaymentInfo? {
        return paymentRepository.findByMemberIdAndIdempotencyKey(
            memberId = memberId,
            idempotencyKey = idempotencyKey,
        )?.let { payment ->
            PaymentInfo.from(payment, orderRepository.findById(payment.orderId)?.status)
        }
    }

    @Transactional(readOnly = true)
    fun preparePayment(memberId: Long, command: PaymentCommand.Request): PaymentPreparation {
        val order = orderRepository.findById(command.orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")

        if (order.memberId != memberId) {
            throw CoreException(ErrorType.NOT_FOUND, "Order not found.")
        }
        if (order.status != OrderStatus.PENDING_PAYMENT) {
            throw CoreException(ErrorType.BAD_REQUEST, "Order is not pending payment.")
        }

        val latestPayment = paymentRepository.findLatestByOrderId(order.id)
        if (latestPayment != null && !latestPayment.isTerminal()) {
            throw CoreException(ErrorType.CONFLICT, "Payment is already in progress.")
        }
        if (latestPayment?.status == PaymentStatus.SUCCESS) {
            throw CoreException(ErrorType.CONFLICT, "Order is already paid.")
        }

        return PaymentPreparation(order = order)
    }

    @Transactional
    fun createPendingPayment(
        preparation: PaymentPreparation,
        command: PaymentCommand.Request,
        transactionKey: String,
        reason: String?,
    ): PaymentInfo {
        val payment = Payment.pending(
            order = preparation.order,
            cardType = command.cardType,
            cardNo = command.cardNo,
            idempotencyKey = command.idempotencyKey,
            transactionKey = transactionKey,
            reason = reason,
        ).let(paymentRepository::save)

        return PaymentInfo.from(payment, preparation.order.status)
    }

    @Transactional
    fun createSyncRequiredPayment(
        preparation: PaymentPreparation,
        command: PaymentCommand.Request,
        reason: String?,
    ): PaymentInfo {
        val payment = Payment.syncRequired(
            order = preparation.order,
            cardType = command.cardType,
            cardNo = command.cardNo,
            idempotencyKey = command.idempotencyKey,
            reason = reason,
        ).let(paymentRepository::save)

        return PaymentInfo.from(payment, preparation.order.status)
    }

    @Transactional
    fun createFailedPayment(
        preparation: PaymentPreparation,
        command: PaymentCommand.Request,
        reason: String?,
    ): PaymentInfo {
        val payment = Payment.failed(
            order = preparation.order,
            cardType = command.cardType,
            cardNo = command.cardNo,
            idempotencyKey = command.idempotencyKey,
            reason = reason,
        ).let(paymentRepository::save)

        return PaymentInfo.from(payment, preparation.order.status)
    }

    @Transactional(readOnly = true)
    fun getPayment(memberId: Long, paymentId: Long): PaymentInfo {
        val payment = paymentRepository.findByMemberIdAndId(memberId = memberId, paymentId = paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found.")
        val order = orderRepository.findById(payment.orderId)

        return PaymentInfo.from(payment, order?.status)
    }

    @Transactional
    fun applyPgTransaction(
        paymentId: Long,
        transactionKey: String,
        status: PgTransactionStatus,
        reason: String?,
    ): PaymentInfo {
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found.")
        val order = orderRepository.findByIdForUpdate(payment.orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")

        return applyPgTransaction(
            payment = payment,
            order = order,
            transactionKey = transactionKey,
            status = status,
            reason = reason,
        )
    }

    @Transactional
    fun handleCallback(command: PaymentCommand.Callback): PaymentInfo {
        val payment = paymentRepository.findByTransactionKeyForUpdate(command.transactionKey)
            ?: return recoverCallbackPayment(command)
        val order = orderRepository.findByIdForUpdate(payment.orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")

        if (payment.orderNumber != command.orderNumber || payment.amount != command.amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "Payment callback does not match payment request.")
        }

        return applyPgTransaction(
            payment = payment,
            order = order,
            transactionKey = command.transactionKey,
            status = command.status,
            reason = command.reason,
        )
    }

    @Transactional
    fun markConfirmationNotFound(paymentId: Long): PaymentInfo {
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found.")
        val order = orderRepository.findByIdForUpdate(payment.orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")

        payment.fail("Payment gateway has no transaction for this order.")

        return paymentRepository.save(payment)
            .let { PaymentInfo.from(it, order.status) }
    }

    private fun applyPgTransaction(
        payment: Payment,
        order: Order,
        transactionKey: String,
        status: PgTransactionStatus,
        reason: String?,
    ): PaymentInfo {
        when (status) {
            PgTransactionStatus.PENDING -> {
                if (payment.status == PaymentStatus.SYNC_REQUIRED) {
                    payment.markPending(transactionKey = transactionKey, reason = reason)
                    return paymentRepository.save(payment)
                        .let { PaymentInfo.from(it, order.status) }
                }

                return PaymentInfo.from(payment, order.status)
            }
            PgTransactionStatus.SUCCESS -> {
                val shouldCompleteOrder = order.status == OrderStatus.PENDING_PAYMENT
                payment.succeed(transactionKey = transactionKey, reason = reason)
                if (shouldCompleteOrder) {
                    order.completePayment()
                    orderRepository.updateStatus(order)
                }
            }
            PgTransactionStatus.FAILED -> {
                val shouldRestoreReservation =
                    payment.status != PaymentStatus.FAILED && order.status == OrderStatus.PENDING_PAYMENT
                payment.fail(transactionKey = transactionKey, reason = reason)
                if (shouldRestoreReservation) {
                    order.failPayment()
                    restoreOrderReservations(order)
                    orderRepository.updateStatus(order)
                }
            }
        }

        return paymentRepository.save(payment)
            .let { PaymentInfo.from(it, order.status) }
    }

    private fun recoverCallbackPayment(command: PaymentCommand.Callback): PaymentInfo {
        val order = orderRepository.findByOrderNumberForUpdate(command.orderNumber)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")
        val payment = Payment.pending(
            order = order,
            cardType = command.cardType,
            cardNo = command.cardNo,
            idempotencyKey = callbackIdempotencyKey(command.transactionKey),
            transactionKey = command.transactionKey,
            reason = command.reason,
        )

        return applyPgTransaction(
            payment = paymentRepository.save(payment),
            order = order,
            transactionKey = command.transactionKey,
            status = command.status,
            reason = command.reason,
        )
    }

    private fun callbackIdempotencyKey(transactionKey: String): String {
        return "pg-callback:$transactionKey"
    }

    private fun restoreOrderReservations(order: Order) {
        val productIds = order.items.map { it.productId }
        val inventories = inventoryService.getInventoriesForUpdate(productIds)
        val inventoryByProductId = inventories.associateBy { it.productId }

        order.items.forEach { item ->
            inventoryByProductId[item.productId]?.restore(item.quantity)
        }
        inventoryService.updateInventories(inventories)

        order.couponIssueId
            ?.let(couponService::getCouponIssueForUpdate)
            ?.also { it.restoreUse() }
            ?.let(couponService::saveCouponIssue)
    }
}

data class PaymentPreparation(
    val order: Order,
)
