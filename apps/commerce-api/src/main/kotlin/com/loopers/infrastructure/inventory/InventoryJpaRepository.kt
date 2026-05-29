package com.loopers.infrastructure.inventory

import org.springframework.data.jpa.repository.JpaRepository

interface InventoryJpaRepository : JpaRepository<InventoryEntity, Long> {
    fun findByProductId(productId: Long): InventoryEntity?
}
