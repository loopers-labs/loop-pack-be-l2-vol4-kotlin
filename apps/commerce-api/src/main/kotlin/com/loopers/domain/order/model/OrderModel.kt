package com.loopers.domain.order.model

import com.loopers.domain.order.exception.InvalidOrderException
import com.loopers.domain.product.vo.Money

data class OrderModel(
    val id: Long = 0L,
    val orderedUserId: Long,
    val idempotencyKey: String? = null,
    val issuedCouponIdOrNull: Long? = null,
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
            issuedCouponIdOrNull: Long? = null,
            discountPrice: Money = Money.of(0),
        ): OrderModel {
            validateUserId(orderedUserId)
            validateIssuedCouponId(issuedCouponIdOrNull)
            validateItems(items)
            val totalPrice = calculateTotalPrice(items)
            validateDiscount(totalPrice, discountPrice)
            return OrderModel(
                orderedUserId = orderedUserId,
                idempotencyKey = idempotencyKey,
                issuedCouponIdOrNull = issuedCouponIdOrNull,
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
            issuedCouponIdOrNull: Long? = null,
            items: List<OrderItemModel>,
            totalPrice: Long,
            discountPrice: Long,
            paymentPrice: Long,
        ): OrderModel {
            validatePersistedId(id)
            validateUserId(orderedUserId)
            validateIssuedCouponId(issuedCouponIdOrNull)
            validateItems(items)
            validateDiscount(Money.of(totalPrice), Money.of(discountPrice))
            return OrderModel(
                id = id,
                orderedUserId = orderedUserId,
                idempotencyKey = idempotencyKey,
                issuedCouponIdOrNull = issuedCouponIdOrNull,
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

        private fun validatePersistedId(id: Long) {
            if (id <= 0) {
                throw InvalidOrderException("저장된 주문 ID는 양수여야 합니다.")
            }
        }

        private fun validateUserId(orderedUserId: Long) {
            if (orderedUserId <= 0) {
                throw InvalidOrderException("주문자 ID는 양수여야 합니다.")
            }
        }

        private fun validateIssuedCouponId(issuedCouponIdOrNull: Long?) {
            if (issuedCouponIdOrNull != null && issuedCouponIdOrNull <= 0) {
                throw InvalidOrderException("발급 쿠폰 ID는 양수여야 합니다.")
            }
        }

        private fun validateItems(items: List<OrderItemModel>) {
            if (items.isEmpty()) {
                throw InvalidOrderException("주문은 하나 이상의 상품을 포함해야 합니다.")
            }
        }

        private fun validateDiscount(totalPrice: Money, discountPrice: Money) {
            if (discountPrice.value > totalPrice.value) {
                throw InvalidOrderException("할인 금액은 주문 총액을 초과할 수 없습니다.")
            }
        }
    }
}
