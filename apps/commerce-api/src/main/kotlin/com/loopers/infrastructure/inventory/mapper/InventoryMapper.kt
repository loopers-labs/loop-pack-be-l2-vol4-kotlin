package com.loopers.infrastructure.inventory.mapper

import com.loopers.domain.inventory.model.Inventory
import com.loopers.infrastructure.inventory.entity.InventoryEntity

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
