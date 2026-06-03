package com.loopers.domain.stock

data class Stock(
    val id: Long = 0L,
    val productId: Long,
    val quantity: Int,
) {
    init {
        require(productId > 0) { "상품 ID는 0보다 커야 합니다." }
        require(quantity >= 0) { "재고 수량은 0 이상이어야 합니다." }
    }

    fun decrease(amount: Int): Stock {
        require(amount > 0) { "차감 수량은 0보다 커야 합니다." }
        require(quantity >= amount) { "재고가 부족합니다. 현재: $quantity, 요청: $amount" }
        return copy(quantity = quantity - amount)
    }

    fun restore(amount: Int): Stock {
        require(amount > 0) { "복원 수량은 0보다 커야 합니다." }
        return copy(quantity = quantity + amount)
    }

    fun updateQuantity(quantity: Int): Stock {
        require(quantity >= 0) { "재고 수량은 0 이상이어야 합니다." }
        return copy(quantity = quantity)
    }

    companion object {
        fun create(productId: Long, quantity: Int): Stock = Stock(productId = productId, quantity = quantity)
    }
}
