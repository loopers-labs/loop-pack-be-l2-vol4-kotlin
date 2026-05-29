package com.loopers.infrastructure.inventory

import com.loopers.domain.inventory.Inventory
import com.loopers.domain.inventory.InventoryRepository
import org.springframework.stereotype.Component

@Component
class InventoryRepositoryImpl(
    private val inventoryJpaRepository: InventoryJpaRepository,
) : InventoryRepository {
    override fun findByProductId(productId: Long): Inventory? {
        return inventoryJpaRepository.findByProductId(productId)
            ?.let(InventoryMapper::toDomain)
    }

    override fun save(inventory: Inventory): Inventory {
        return inventoryJpaRepository.save(InventoryMapper.toEntity(inventory))
            .let(InventoryMapper::toDomain)
    }
}
