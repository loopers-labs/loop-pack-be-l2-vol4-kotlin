package com.loopers.infrastructure.inventory

import com.loopers.domain.BaseEntity
import com.loopers.domain.inventory.Inventory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "inventory")
class InventoryEntity(
    @Column(name = "product_id", nullable = false, unique = true)
    var productId: Long,

    @Column(nullable = false)
    var quantity: Long,
) : BaseEntity() {
    fun update(domain: Inventory) {
        productId = domain.productId
        quantity = domain.quantity
    }
}
