package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderCancelReason
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class OrderApplicationService(
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun createPending(command: OrderCommand.Checkout, discountAmount: Long = 0L): OrderInfo.Detail {
        if (command.items.isEmpty()) throw CoreException(ErrorType.BAD_REQUEST, "주문 품목은 비어있을 수 없습니다.")
        val totalAmount = command.items.sumOf { it.priceSnapshot * it.quantity }
        val order = orderRepository.save(
            Order(
                userId = command.userId,
                reservationExpiresAt = command.reservationExpiresAt,
                deliveryAddress = command.deliveryAddress,
                deliveryRequest = command.deliveryRequest,
                phoneNumber = command.phoneNumber,
                couponId = command.couponId,
                totalAmount = totalAmount,
                discountAmount = discountAmount,
                paymentAmount = totalAmount - discountAmount,
            ),
        )
        val items = orderRepository.saveItems(
            command.items.map {
                OrderItem(
                    orderId = order.id,
                    productId = it.productId,
                    productNameSnapshot = it.productNameSnapshot,
                    brandNameSnapshot = it.brandNameSnapshot,
                    priceSnapshot = it.priceSnapshot,
                    quantity = it.quantity,
                )
            },
        )
        return OrderInfo.Detail.from(order, items)
    }

    @Transactional(readOnly = true)
    fun getDetail(orderId: Long): OrderInfo.Detail {
        val order = getOrder(orderId)
        return OrderInfo.Detail.from(order, orderRepository.findItemsByOrderId(order.id))
    }

    @Transactional(readOnly = true)
    fun getDetailsByProductId(productId: Long): List<OrderInfo.Detail> =
        orderRepository.findByProductId(productId)
            .map { order -> OrderInfo.Detail.from(order, orderRepository.findItemsByOrderId(order.id)) }

    @Transactional
    fun completePaymentPending(orderId: Long): OrderInfo.Detail {
        requireUpdated(orderRepository.completeFromPaymentPending(orderId))
        return getDetail(orderId)
    }

    @Transactional
    fun completeFailed(orderId: Long): OrderInfo.Detail {
        requireUpdated(orderRepository.completeFromFailed(orderId))
        return getDetail(orderId)
    }

    @Transactional
    fun markCompletionFailed(orderId: Long): OrderInfo.Detail {
        requireUpdated(orderRepository.markCompletionFailed(orderId))
        return getDetail(orderId)
    }

    @Transactional
    fun markCompletedAsFailed(orderId: Long): OrderInfo.Detail {
        requireUpdated(orderRepository.markCompletedAsFailed(orderId))
        return getDetail(orderId)
    }

    @Transactional
    fun expirePaymentPending(orderId: Long): OrderInfo.Detail {
        requireUpdated(orderRepository.expirePaymentPending(orderId))
        return getDetail(orderId)
    }

    @Transactional
    fun cancelPaymentPending(orderId: Long, reason: OrderCancelReason): OrderInfo.Detail {
        val updatedCount = orderRepository.cancelPaymentPending(orderId, reason)
        requireUpdated(updatedCount)
        return getDetail(orderId)
    }

    @Transactional
    fun cancelCompleted(orderId: Long, reason: OrderCancelReason): OrderInfo.Detail {
        val updatedCount = orderRepository.cancelCompleted(orderId, reason)
        requireUpdated(updatedCount)
        return getDetail(orderId)
    }

    @Transactional
    fun cancelFailedByOperator(orderId: Long, reason: OrderCancelReason): OrderInfo.Detail {
        requireUpdated(orderRepository.cancelFailedByOperator(orderId, reason))
        return getDetail(orderId)
    }

    @Transactional
    fun startShipping(orderId: Long): OrderInfo.Detail {
        val updatedCount = orderRepository.startShippingCompleted(orderId)
        requireUpdated(updatedCount)
        return getDetail(orderId)
    }

    @Transactional(readOnly = true)
    fun findExpiredPaymentPending(now: LocalDateTime): List<Order> = orderRepository.findExpiredPaymentPending(now)

    @Transactional(readOnly = true)
    fun getOrder(orderId: Long): Order =
        orderRepository.findById(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")

    private fun requireUpdated(updatedCount: Int) {
        if (updatedCount != 1) {
            throw CoreException(ErrorType.CONFLICT, "주문 상태가 변경되어 요청을 처리할 수 없습니다.")
        }
    }
}
