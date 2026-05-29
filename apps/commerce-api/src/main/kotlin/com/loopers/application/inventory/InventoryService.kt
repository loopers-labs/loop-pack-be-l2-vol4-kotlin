package com.loopers.application.inventory

import com.loopers.domain.inventory.Inventory
import com.loopers.domain.inventory.InventoryRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class InventoryService(
    private val inventoryRepository: InventoryRepository,
) {
    @Transactional(readOnly = true)
    fun getInventory(productId: Long): Inventory {
        return inventoryRepository.findByProductId(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "Inventory not found.")
    }

    @Transactional
    fun createInventory(productId: Long, quantity: Long): Inventory {
        return Inventory(
            productId = productId,
            quantity = quantity,
        ).let(inventoryRepository::save)
    }
}
