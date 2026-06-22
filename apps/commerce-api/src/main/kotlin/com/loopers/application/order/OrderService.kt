package com.loopers.application.order

import com.loopers.application.order.dto.OrderInfo
import com.loopers.application.order.dto.OrderSummaryInfo
import com.loopers.domain.order.model.Order
import com.loopers.domain.order.repository.OrderRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class OrderService(
    private val orderRepository: OrderRepository,
) {
    @Transactional(readOnly = true)
    fun getOrders(page: Int, size: Int): Page<OrderSummaryInfo> {
        return orderRepository.findAll(page = page, size = size)
            .map(OrderSummaryInfo::from)
    }

    @Transactional(readOnly = true)
    fun getOrder(orderId: Long): OrderInfo {
        return orderRepository.findById(orderId)
            ?.let(OrderInfo::from)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")
    }

    @Transactional(readOnly = true)
    fun getOrders(
        memberId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
    ): List<OrderSummaryInfo> {
        return orderRepository.findAllByMemberIdAndOrderedAtBetween(
            memberId = memberId,
            startAt = startAt,
            endAt = endAt,
        ).map(OrderSummaryInfo::from)
    }

    @Transactional(readOnly = true)
    fun getOrder(memberId: Long, orderId: Long): OrderInfo {
        return orderRepository.findByMemberIdAndId(memberId = memberId, orderId = orderId)
            ?.let(OrderInfo::from)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Order not found.")
    }

    fun save(order: Order): Order {
        return orderRepository.save(order)
    }
}
