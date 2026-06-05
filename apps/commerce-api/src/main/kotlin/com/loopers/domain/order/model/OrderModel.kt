package com.loopers.domain.order.model

import com.loopers.domain.order.exception.InvalidOrderException
import com.loopers.domain.product.vo.Money

data class OrderModel(
    val id: Long = 0L,
    val orderedUserId: Long,
    val idempotencyKey: String? = null,
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

    companion object {
        fun create(
            orderedUserId: Long,
            items: List<OrderItemModel>,
            idempotencyKey: String? = null,
        ): OrderModel {
            validateUserId(orderedUserId)
            validateItems(items)
            val totalPrice = calculateTotalPrice(items)
            val discountPrice = Money.of(0)
            return OrderModel(
                orderedUserId = orderedUserId,
                idempotencyKey = idempotencyKey,
                items = items,
                totalPrice = totalPrice,
                discountPrice = discountPrice,
                paymentPrice = Money.of(totalPrice.value - discountPrice.value),
            )
        }

        fun fromPersisted(
            id: Long,
            orderedUserId: Long,
            idempotencyKey: String? = null,
            items: List<OrderItemModel>,
            totalPrice: Long,
            discountPrice: Long,
            paymentPrice: Long,
        ): OrderModel {
            validateId(id)
            validateUserId(orderedUserId)
            validateItems(items)
            return OrderModel(
                id = id,
                orderedUserId = orderedUserId,
                idempotencyKey = idempotencyKey,
                items = items,
                totalPrice = Money.of(totalPrice),
                discountPrice = Money.of(discountPrice),
                paymentPrice = Money.of(paymentPrice),
            )
        }

        private fun calculateTotalPrice(items: List<OrderItemModel>): Money =
            Money.of(items.sumOf { it.linePrice.value })

        private fun validateId(id: Long) {
            if (id < 0) {
                throw InvalidOrderException("주문 ID는 음수일 수 없습니다.")
            }
        }

        private fun validateUserId(orderedUserId: Long) {
            if (orderedUserId <= 0) {
                throw InvalidOrderException("주문자 ID는 양수여야 합니다.")
            }
        }

        private fun validateItems(items: List<OrderItemModel>) {
            if (items.isEmpty()) {
                throw InvalidOrderException("주문은 하나 이상의 상품을 포함해야 합니다.")
            }
        }
    }
}
