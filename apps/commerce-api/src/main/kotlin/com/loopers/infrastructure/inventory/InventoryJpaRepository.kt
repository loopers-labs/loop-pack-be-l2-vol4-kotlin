package com.loopers.infrastructure.inventory

import com.loopers.domain.inventory.Inventory
import org.springframework.data.jpa.repository.JpaRepository

interface InventoryJpaRepository : JpaRepository<Inventory, Long> {
    fun findByProductIdAndDeletedAtIsNull(productId: Long): Inventory?

    fun findByProductIdInAndDeletedAtIsNull(productIds: List<Long>): List<Inventory>
}
