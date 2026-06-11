package com.loopers.inventory.domain

import com.loopers.domain.BaseEntity
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "inventory")
class Inventory(
    productId: Long,
    quantity: Long,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, updatable = false)
    val productId: Long = productId

    @Column(name = "quantity", nullable = false)
    var quantity: Long = quantity
        private set

    init {
        if (quantity < 0) {
            throw BadRequestException(InventoryErrorCode.INVALID_QUANTITY)
        }
    }

    fun decrease(amount: Long) {
        if (amount <= 0) {
            throw BadRequestException(InventoryErrorCode.INVALID_QUANTITY)
        }
        if (quantity < amount) {
            throw ConflictException(InventoryErrorCode.STOCK_INSUFFICIENT)
        }
        quantity -= amount
    }

    fun increase(amount: Long) {
        if (amount <= 0) {
            throw BadRequestException(InventoryErrorCode.INVALID_QUANTITY)
        }
        quantity += amount
    }

    companion object {
        fun createFor(productId: Long, quantity: Long): Inventory =
            Inventory(productId, quantity)
    }
}
