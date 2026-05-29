package com.loopers.domain.inventory

interface InventoryRepository {
    fun findByProductId(productId: Long): Inventory?

    fun save(inventory: Inventory): Inventory
}
