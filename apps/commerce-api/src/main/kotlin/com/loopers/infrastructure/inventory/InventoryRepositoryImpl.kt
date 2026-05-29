package com.loopers.infrastructure.inventory

import com.loopers.domain.inventory.Inventory
import com.loopers.domain.inventory.InventoryRepository
import org.springframework.stereotype.Repository

@Repository
class InventoryRepositoryImpl(
    private val inventoryJpaRepository: InventoryJpaRepository,
) : InventoryRepository {
    override fun save(inventory: Inventory): Inventory =
        inventoryJpaRepository.save(inventory)

    override fun findByProductId(productId: Long): Inventory? =
        inventoryJpaRepository.findByProductIdAndDeletedAtIsNull(productId)

    override fun findAllByProductIdIn(productIds: List<Long>): List<Inventory> =
        inventoryJpaRepository.findByProductIdInAndDeletedAtIsNull(productIds)
}
