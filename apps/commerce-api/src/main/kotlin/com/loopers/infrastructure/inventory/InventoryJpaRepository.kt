package com.loopers.infrastructure.inventory

import com.loopers.domain.inventory.Inventory
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InventoryJpaRepository : JpaRepository<Inventory, Long> {
    fun findByProductIdAndDeletedAtIsNull(productId: Long): Inventory?

    fun findByProductIdInAndDeletedAtIsNull(productIds: List<Long>): List<Inventory>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.productId in :productIds and i.deletedAt is null order by i.productId")
    fun findAllForUpdateByProductIdIn(@Param("productIds") productIds: List<Long>): List<Inventory>
}
