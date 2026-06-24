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
    @Transactional
    fun preparePayment(memberId: Long, command: PaymentCommand.Request): PaymentInfo {
        val order = orderRepository.findByIdForUpdate(command.orderId)
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

        val payment = Payment.request(
            order = order,
            cardType = command.cardType,
            cardNo = command.cardNo,
        ).let(paymentRepository::save)

        return PaymentInfo.from(payment, order.status)
    }

    @Transactional
    fun markPending(paymentId: Long, transactionKey: String, reason: String?): PaymentInfo {
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found.")
        val order = orderRepository.findByIdForUpdate(payment.orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")

        payment.markPending(transactionKey = transactionKey, reason = reason)

        return paymentRepository.save(payment)
            .let { PaymentInfo.from(it, order.status) }
    }

    @Transactional
    fun markPendingConfirmation(paymentId: Long, reason: String?): PaymentInfo {
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found.")
        val order = orderRepository.findByIdForUpdate(payment.orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")

        payment.markPendingConfirmation(reason)

        return paymentRepository.save(payment)
            .let { PaymentInfo.from(it, order.status) }
    }

    @Transactional
    fun markRequestFailed(paymentId: Long, reason: String?): PaymentInfo {
        val payment = paymentRepository.findByIdForUpdate(paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found.")
        val order = orderRepository.findByIdForUpdate(payment.orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")

        payment.markRequestFailed(reason)

        return paymentRepository.save(payment)
            .let { PaymentInfo.from(it, order.status) }
    }

    @Transactional(readOnly = true)
    fun getPayment(memberId: Long, paymentId: Long): PaymentInfo {
        val payment = paymentRepository.findByMemberIdAndId(memberId = memberId, paymentId = paymentId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found.")
        val order = orderRepository.findById(payment.orderId)

        return PaymentInfo.from(payment, order?.status)
    }

    @Transactional
    fun handleCallback(command: PaymentCommand.Callback): PaymentInfo {
        val payment = paymentRepository.findByTransactionKeyForUpdate(command.transactionKey)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Payment not found.")
        val order = orderRepository.findByIdForUpdate(payment.orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")

        if (payment.orderNumber != command.orderNumber || payment.amount != command.amount) {
            throw CoreException(ErrorType.BAD_REQUEST, "Payment callback does not match payment request.")
        }

        when (command.status) {
            PgTransactionStatus.PENDING -> return PaymentInfo.from(payment, order.status)
            PgTransactionStatus.SUCCESS -> {
                val shouldCompleteOrder = order.status == OrderStatus.PENDING_PAYMENT
                payment.succeed(transactionKey = command.transactionKey, reason = command.reason)
                if (shouldCompleteOrder) {
                    order.completePayment()
                    orderRepository.updateStatus(order)
                }
            }
            PgTransactionStatus.FAILED -> {
                val shouldRestoreReservation =
                    payment.status != PaymentStatus.FAILED && order.status == OrderStatus.PENDING_PAYMENT
                payment.fail(transactionKey = command.transactionKey, reason = command.reason)
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
