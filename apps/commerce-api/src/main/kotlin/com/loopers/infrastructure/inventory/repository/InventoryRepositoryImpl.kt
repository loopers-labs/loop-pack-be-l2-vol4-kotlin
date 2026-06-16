package com.loopers.infrastructure.inventory.repository

import com.loopers.domain.inventory.model.Inventory
import com.loopers.domain.inventory.repository.InventoryRepository
import com.loopers.infrastructure.inventory.mapper.InventoryMapper
import org.springframework.stereotype.Component

@Component
class InventoryRepositoryImpl(
    private val inventoryJpaRepository: InventoryJpaRepository,
) : InventoryRepository {
    override fun findByProductId(productId: Long): Inventory? {
        return inventoryJpaRepository.findByProductId(productId)
            ?.let(InventoryMapper::toDomain)
    }

    override fun findAllByProductIdsForUpdate(productIds: Collection<Long>): List<Inventory> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        return inventoryJpaRepository.findAllByProductIdInForUpdate(productIds)
            .map(InventoryMapper::toDomain)
    }

    override fun save(inventory: Inventory): Inventory {
        return inventoryJpaRepository.save(InventoryMapper.toEntity(inventory))
            .let(InventoryMapper::toDomain)
    }

    override fun updateAll(inventories: Collection<Inventory>): List<Inventory> {
        if (inventories.isEmpty()) {
            return emptyList()
        }

        val inventoryByProductId = inventories.associateBy { it.productId }
        val entities = inventoryJpaRepository.findAllByProductIdIn(inventoryByProductId.keys)
            .onEach { entity ->
                inventoryByProductId[entity.productId]?.let(entity::update)
            }

        return inventoryJpaRepository.saveAll(entities)
            .map(InventoryMapper::toDomain)
    }
}
