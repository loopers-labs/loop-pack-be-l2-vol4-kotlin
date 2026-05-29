package com.loopers.infrastructure.inventory

import com.loopers.domain.inventory.Inventory

object InventoryMapper {
    fun toDomain(inventory: InventoryEntity): Inventory {
        return Inventory(
            id = inventory.id,
            productId = inventory.productId,
            quantity = inventory.quantity,
        )
    }

    fun toEntity(inventory: Inventory): InventoryEntity {
        return InventoryEntity(
            productId = inventory.productId,
            quantity = inventory.quantity,
        )
    }
}
