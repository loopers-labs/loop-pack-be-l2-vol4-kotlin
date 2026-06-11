package com.loopers.inventory.infrastructure

import com.loopers.inventory.domain.Inventory
import com.loopers.inventory.domain.InventoryRepository
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

    override fun findAllByProductIdInForUpdate(productIds: List<Long>): List<Inventory> =
        inventoryJpaRepository.findAllForUpdateByProductIdIn(productIds)
}
