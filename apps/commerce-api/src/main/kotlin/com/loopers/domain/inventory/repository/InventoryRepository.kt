package com.loopers.domain.inventory.repository

import com.loopers.domain.inventory.model.Inventory

interface InventoryRepository {
    fun findByProductId(productId: Long): Inventory?

    fun findAllByProductIdsForUpdate(productIds: Collection<Long>): List<Inventory>

    fun save(inventory: Inventory): Inventory

    fun updateAll(inventories: Collection<Inventory>): List<Inventory>
}
