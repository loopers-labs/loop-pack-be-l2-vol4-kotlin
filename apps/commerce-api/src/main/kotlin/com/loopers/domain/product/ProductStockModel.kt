package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_stocks")
class ProductStockModel(
    productId: Long,
    quantity: Int,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, unique = true)
    var productId: Long = productId
        protected set

    @Column(nullable = false)
    var quantity: Int = quantity
        protected set

    init {
        validate(productId = productId, quantity = quantity)
    }

    fun hasEnough(quantity: Int): Boolean {
        validateOrderQuantity(quantity)
        return this.quantity >= quantity
    }

    fun deduct(quantity: Int) {
        validateOrderQuantity(quantity)
        if (!hasEnough(quantity)) throw CoreException(ErrorType.CONFLICT, "상품 재고가 부족합니다.")
        this.quantity -= quantity
    }

    fun restore(quantity: Int) {
        validateOrderQuantity(quantity)
        this.quantity += quantity
    }

    companion object {
        private fun validate(productId: Long, quantity: Int) {
            if (productId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "상품 ID는 양수여야 합니다.")
            if (quantity < 0) throw CoreException(ErrorType.BAD_REQUEST, "상품 재고는 음수일 수 없습니다.")
        }

        private fun validateOrderQuantity(quantity: Int) {
            if (quantity <= 0) throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 양수여야 합니다.")
        }
    }
}
