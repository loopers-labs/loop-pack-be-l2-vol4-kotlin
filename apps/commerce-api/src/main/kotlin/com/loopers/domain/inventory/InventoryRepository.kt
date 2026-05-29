package com.loopers.domain.inventory

interface InventoryRepository {
    fun save(inventory: Inventory): Inventory

    fun findByProductId(productId: Long): Inventory?

    fun findAllByProductIdIn(productIds: List<Long>): List<Inventory>
}
