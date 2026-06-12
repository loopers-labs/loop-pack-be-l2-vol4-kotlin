package com.loopers.application.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderAmounts
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
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
    fun markPaid(id: Long): OrderConfirmResult {
        // 조건부 UPDATE 를 먼저 실행해야 한다. getOrder(SELECT) 를 먼저 하면 REPEATABLE_READ
        // 스냅샷이 고정되어, 이후 재조회가 다른 트랜잭션의 최신 상태(PAID/CANCELED)를 못 볼 수 있다.
        if (orderRepository.markPaidIfPending(id)) {
            return OrderConfirmResult.Confirmed(getOrder(id))
        }
        val current = getOrder(id)
        return when (current.status) {
            OrderStatus.PAID -> OrderConfirmResult.AlreadyPaid(current)
            else -> OrderConfirmResult.AlreadyTerminated(current)
        }
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
