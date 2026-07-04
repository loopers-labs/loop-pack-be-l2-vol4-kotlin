package com.loopers.domain.order.model

import com.loopers.domain.order.constant.OrderErrorMessages
import com.loopers.domain.order.exception.InvalidOrderException
import com.loopers.domain.product.vo.Money

data class OrderModel(
    val id: Long = 0L,
    val orderedUserId: Long,
    val idempotencyKey: String? = null,
    val issuedCouponId: Long? = null,
    val status: OrderStatus,
    val items: List<OrderItemModel>,
    val totalPrice: Money,
    val discountPrice: Money,
    val paymentPrice: Money,
) {
    fun belongsTo(userId: Long): Boolean = orderedUserId == userId

    fun withId(id: Long): OrderModel {
        validateId(id)
        return copy(
            id = id,
            items = items.map { it.withOrderId(id) },
        )
    }

    fun markOrdered(): OrderModel {
        if (status == OrderStatus.ORDERED) {
            return this
        }
        validatePendingTransition()
        return copy(status = OrderStatus.ORDERED)
    }

    fun markPaymentFailed(): OrderModel {
        if (status == OrderStatus.PAYMENT_FAILED) {
            return this
        }
        validatePendingTransition()
        return copy(status = OrderStatus.PAYMENT_FAILED)
    }

    fun detachCoupon(): OrderModel = copy(issuedCouponId = null)

    companion object {
        fun create(
            orderedUserId: Long,
            items: List<OrderItemModel>,
            idempotencyKey: String? = null,
            issuedCouponId: Long? = null,
            discountPrice: Money = Money.of(0),
        ): OrderModel {
            validateUserId(orderedUserId)
            validateIssuedCouponId(issuedCouponId)
            validateItems(items)
            val totalPrice = calculateTotalPrice(items)
            validateDiscount(totalPrice, discountPrice)
            return OrderModel(
                orderedUserId = orderedUserId,
                idempotencyKey = idempotencyKey,
                issuedCouponId = issuedCouponId,
                status = OrderStatus.PAYMENT_PENDING,
                items = items,
                totalPrice = totalPrice,
                discountPrice = discountPrice,
                paymentPrice = totalPrice - discountPrice,
            )
        }

        fun fromPersisted(
            id: Long,
            orderedUserId: Long,
            idempotencyKey: String? = null,
            issuedCouponId: Long? = null,
            status: OrderStatus = OrderStatus.PAYMENT_PENDING,
            items: List<OrderItemModel>,
            totalPrice: Long,
            discountPrice: Long,
            paymentPrice: Long,
        ): OrderModel {
            validatePersistedId(id)
            validateUserId(orderedUserId)
            validateIssuedCouponId(issuedCouponId)
            validateItems(items)
            validateDiscount(Money.of(totalPrice), Money.of(discountPrice))
            return OrderModel(
                id = id,
                orderedUserId = orderedUserId,
                idempotencyKey = idempotencyKey,
                issuedCouponId = issuedCouponId,
                status = status,
                items = items,
                totalPrice = Money.of(totalPrice),
                discountPrice = Money.of(discountPrice),
                paymentPrice = Money.of(paymentPrice),
            )
        }

        private fun calculateTotalPrice(items: List<OrderItemModel>): Money =
            items.fold(Money.ZERO) { acc, item -> acc + item.linePrice }

        private fun validateId(id: Long) {
            if (id < 0) {
                throw InvalidOrderException(OrderErrorMessages.ORDER_ID_NEGATIVE)
            }
        }

        private fun validatePersistedId(id: Long) {
            if (id <= 0) {
                throw InvalidOrderException(OrderErrorMessages.PERSISTED_ORDER_ID_MUST_BE_POSITIVE)
            }
        }

        private fun validateUserId(orderedUserId: Long) {
            if (orderedUserId <= 0) {
                throw InvalidOrderException(OrderErrorMessages.ORDERED_USER_ID_MUST_BE_POSITIVE)
            }
        }

        private fun validateIssuedCouponId(issuedCouponId: Long?) {
            if (issuedCouponId != null && issuedCouponId <= 0) {
                throw InvalidOrderException(OrderErrorMessages.ISSUED_COUPON_ID_MUST_BE_POSITIVE)
            }
        }

        private fun validateItems(items: List<OrderItemModel>) {
            if (items.isEmpty()) {
                throw InvalidOrderException(OrderErrorMessages.ORDER_MUST_HAVE_ITEMS)
            }
        }

        private fun validateDiscount(totalPrice: Money, discountPrice: Money) {
            if (discountPrice > totalPrice) {
                throw InvalidOrderException(OrderErrorMessages.DISCOUNT_EXCEEDS_TOTAL)
            }
        }
    }

    private fun validatePendingTransition() {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw InvalidOrderException(OrderErrorMessages.INVALID_STATUS_TRANSITION)
        }
    }
}
