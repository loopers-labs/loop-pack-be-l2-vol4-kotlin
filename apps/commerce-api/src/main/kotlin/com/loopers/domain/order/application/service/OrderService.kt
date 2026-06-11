package com.loopers.domain.order.application.service

import com.loopers.domain.order.exception.OrderDomainException
import com.loopers.domain.order.model.OrderItemModel
import com.loopers.domain.order.model.OrderModel
import com.loopers.domain.order.port.OrderRepository
import com.loopers.domain.product.exception.ProductDomainException
import com.loopers.domain.product.vo.Money
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class OrderService(
    private val orderRepository: OrderRepository,
) {
    @Transactional
    fun placeOrder(
        orderedUserId: Long,
        items: List<OrderItemModel>,
        idempotencyKey: String? = null,
        issuedCouponIdOrNull: Long? = null,
        discountPrice: Money = Money.of(0),
    ): OrderModel =
        try {
            orderRepository.save(
                OrderModel.create(
                    orderedUserId = orderedUserId,
                    items = items,
                    idempotencyKey = idempotencyKey,
                    issuedCouponIdOrNull = issuedCouponIdOrNull,
                    discountPrice = discountPrice,
                ),
            )
        } catch (e: OrderDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        } catch (e: ProductDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }

    @Transactional(readOnly = true)
    fun findById(orderId: Long): OrderModel =
        orderRepository.findById(orderId) ?: throw CoreException(ErrorType.NOT_FOUND)

    @Transactional(readOnly = true)
    fun findByIdempotencyKey(idempotencyKey: String): OrderModel? =
        orderRepository.findByIdempotencyKey(idempotencyKey)

    @Transactional(readOnly = true)
    fun findByOrderedUserId(
        orderedUserId: Long,
        startAt: ZonedDateTime?,
        endAt: ZonedDateTime?,
    ): List<OrderModel> =
        orderRepository.findByOrderedUserId(
            orderedUserId = orderedUserId,
            startAt = startAt,
            endAt = endAt,
        )

    @Transactional(readOnly = true)
    fun findAll(page: Int, size: Int): List<OrderModel> =
        orderRepository.findAll(page, size)
}
