package com.loopers.domain.order

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.time.ZonedDateTime

class OrderService(
    private val orderRepositoryPort: OrderRepositoryPort,
) {
    fun getById(id: Long): Order =
        orderRepositoryPort.findById(id)
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")

    fun save(order: Order): Order = orderRepositoryPort.save(order)

    fun findAllByUserId(
        userId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
        pageRequest: PageRequest,
    ): PageResult<Order> = orderRepositoryPort.findAllByUserId(userId, startAt, endAt, pageRequest)

    fun findAll(pageRequest: PageRequest): PageResult<Order> = orderRepositoryPort.findAll(pageRequest)

    fun hasIncompleteOrdersForProducts(productIds: List<Long>): Boolean {
        if (productIds.isEmpty()) return false
        return orderRepositoryPort.existsByProductIdInAndStatusIn(
            productIds = productIds,
            statuses = OrderStatus.INCOMPLETE_STATUSES,
        )
    }
}
