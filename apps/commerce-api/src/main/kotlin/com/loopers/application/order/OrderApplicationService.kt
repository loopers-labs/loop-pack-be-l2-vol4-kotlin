package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderAmounts
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderApplicationService(
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun createOrder(
        userId: Long,
        userCouponId: Long? = null,
        items: List<OrderItem>,
        amounts: OrderAmounts,
    ): Order {
        return orderRepository.save(
            Order(
                userId = userId,
                userCouponId = userCouponId,
                items = items,
                amounts = amounts,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun getOrder(id: Long): Order {
        return orderRepository.find(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다. id=$id")
    }

    @Transactional
    fun markPaid(id: Long): Order {
        val order = getOrder(id)
        if (!orderRepository.markPaidIfPending(id)) {
            throw CoreException(ErrorType.CONFLICT, "결제 확정할 수 없는 주문입니다. id=$id")
        }
        order.markPaid()
        return order
    }

    @Transactional
    fun markPaymentFailed(id: Long): Order {
        val order = getOrder(id)
        if (!orderRepository.markPaymentFailedIfPending(id)) {
            throw CoreException(ErrorType.CONFLICT, "결제 실패 처리할 수 없는 주문입니다. id=$id")
        }
        order.markPaymentFailed()
        return order
    }

    @Transactional
    fun cancelOrder(id: Long): Order {
        val order = getOrder(id)
        if (!orderRepository.cancelIfPending(id)) {
            throw CoreException(ErrorType.CONFLICT, "취소할 수 없는 주문입니다. id=$id")
        }
        order.cancel()
        return order
    }
}
