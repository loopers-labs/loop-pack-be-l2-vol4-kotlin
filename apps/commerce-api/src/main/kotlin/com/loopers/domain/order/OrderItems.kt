package com.loopers.domain.order

data class OrderItems(val list: List<OrderItem>) {
    init {
        require(list.isNotEmpty()) { "주문 항목은 최소 1개여야 합니다." }
    }

    val size: Int get() = list.size

    fun totalAmount(): Long = list.sumOf { it.subtotal() }

    fun productIds(): List<Long> = list.map { it.productId }
}
