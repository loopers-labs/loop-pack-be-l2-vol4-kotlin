package com.loopers.domain.order

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
@Transactional(readOnly = true)
class OrderService(
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun createPending(
        userId: Long,
        items: List<OrderItemModel>,
        couponId: Long? = null,
        discountAmount: Long = 0,
    ): OrderModel {
        val order = OrderModel.create(userId, items, couponId, discountAmount)
        return orderRepository.save(order)
    }

    fun getByIdAndUserId(orderId: Long, userId: Long): OrderModel {
        return orderRepository.findActiveByIdAndUserId(orderId, userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")
    }

    /**
     * PENDING 주문만 결제 완료로 전이한다. 이미 PAID 면 멱등하게 무시한다. (콜백 중복 수신 대비)
     */
    @Transactional
    fun payIfPending(orderId: Long) {
        val order = getById(orderId)
        if (order.status == OrderStatus.PENDING) {
            order.pay()
            orderRepository.save(order)
        }
    }

    fun getById(orderId: Long): OrderModel {
        return orderRepository.findActiveById(orderId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")
    }

    fun getByUserIdAndPeriod(
        userId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
        pageable: Pageable,
    ): Page<OrderModel> = orderRepository.findActiveByUserIdAndPeriod(userId, startAt, endAt, pageable)

    fun getAll(pageable: Pageable): Page<OrderModel> = orderRepository.findAllActive(pageable)
}
