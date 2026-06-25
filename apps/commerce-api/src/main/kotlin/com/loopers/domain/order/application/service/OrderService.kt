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
        issuedCouponId: Long? = null,
        discountPrice: Money = Money.of(0),
    ): OrderModel =
        try {
            orderRepository.save(
                OrderModel.create(
                    orderedUserId = orderedUserId,
                    items = items,
                    idempotencyKey = idempotencyKey,
                    issuedCouponId = issuedCouponId,
                    discountPrice = discountPrice,
                ),
            )
        } catch (e: OrderDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        } catch (e: ProductDomainException) {
            throw CoreException(ErrorType.BAD_REQUEST, e.message, e)
        }

    @Transactional(readOnly = true)
    fun getById(orderId: Long): OrderModel =
        orderRepository.findByIdOrNull(orderId) ?: throw CoreException(ErrorType.NOT_FOUND)

    @Transactional
    fun markOrdered(orderId: Long): OrderModel =
        orderRepository.update(getById(orderId).markOrdered())

    @Transactional
    fun markPaymentFailed(orderId: Long): OrderModel =
        orderRepository.update(getById(orderId).markPaymentFailed())

    @Transactional
    fun detachCoupon(orderId: Long): OrderModel =
        orderRepository.update(getById(orderId).detachCoupon())

    @Transactional(readOnly = true)
    fun findByIdempotencyKeyOrNull(idempotencyKey: String): OrderModel? =
        orderRepository.findByIdempotencyKeyOrNull(idempotencyKey)

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
