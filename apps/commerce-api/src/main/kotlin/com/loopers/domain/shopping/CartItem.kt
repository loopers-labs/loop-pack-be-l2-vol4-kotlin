package com.loopers.domain.shopping

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "cart_items",
    uniqueConstraints = [UniqueConstraint(name = "uk_cart_items_cart_product", columnNames = ["cart_id", "product_id"])],
)
class CartItem(
    @Column(name = "cart_id", nullable = false)
    val cartId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    quantity: Int,
) : BaseEntity() {
    @Column(name = "quantity", nullable = false)
    var quantity: Int = quantity
        protected set

    init {
        validateQuantity(quantity)
    }

    fun increaseQuantity(quantity: Int) {
        validateQuantity(quantity)
        this.quantity += quantity
    }

    fun changeQuantity(quantity: Int) {
        validateQuantity(quantity)
        this.quantity = quantity
    }

    private fun validateQuantity(quantity: Int) {
        if (quantity < 1) {
            throw CoreException(ErrorType.BAD_REQUEST, "쇼핑카트 수량은 1 이상이어야 합니다.")
        }
    }
}
