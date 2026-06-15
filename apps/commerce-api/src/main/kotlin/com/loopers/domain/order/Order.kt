package com.loopers.domain.order

import java.time.ZonedDateTime

data class Order(
    val id: Long = 0L,
    val userId: Long,
    val status: OrderStatus,
    val totalAmount: Long,
    val usedPoint: Long,
    val orderedAt: ZonedDateTime,
    val items: OrderItems,
    val appliedCoupon: AppliedCoupon? = null,
) {
    init {
        require(userId > 0) { "회원 ID는 0보다 커야 합니다." }
        require(totalAmount >= 0) { "총 주문 금액은 0 이상이어야 합니다." }
        require(usedPoint >= 0) { "사용 포인트는 0 이상이어야 합니다." }
        val discount = appliedCoupon?.discountAmount ?: 0L
        require(discount <= totalAmount) { "할인 금액은 총 주문 금액을 초과할 수 없습니다." }
        require(usedPoint <= totalAmount - discount) { "사용 포인트는 할인 후 금액을 초과할 수 없습니다." }
    }

    val discountAmount: Long get() = appliedCoupon?.discountAmount ?: 0L

    fun getActualAmount(): Long = totalAmount - discountAmount - usedPoint

    fun getEarnPoint(): Long = if (status in EARN_STATUSES) getActualAmount() / EARN_DIVISOR else 0L

    fun updateStatus(next: OrderStatus): Order {
        require(status.canTransitionTo(next)) { "주문 상태 전이가 불가능합니다: $status -> $next" }
        return copy(status = next)
    }

    fun cancel(): Order {
        require(status == OrderStatus.CREATED || status == OrderStatus.PAYMENT_PENDING) {
            "취소할 수 없는 주문 상태입니다: $status"
        }
        return copy(status = OrderStatus.CANCELLED)
    }

    companion object {
        private const val EARN_DIVISOR: Long = 100L
        private val EARN_STATUSES: Set<OrderStatus> = setOf(
            OrderStatus.PAYMENT_COMPLETED,
            OrderStatus.SHIPPING,
            OrderStatus.DELIVERED,
        )

        fun create(
            userId: Long,
            items: OrderItems,
            appliedCoupon: AppliedCoupon? = null,
            orderedAt: ZonedDateTime = ZonedDateTime.now(),
        ): Order = Order(
            userId = userId,
            status = OrderStatus.CREATED,
            totalAmount = items.totalAmount(),
            usedPoint = 0L,
            orderedAt = orderedAt,
            items = items,
            appliedCoupon = appliedCoupon,
        )
    }
}
