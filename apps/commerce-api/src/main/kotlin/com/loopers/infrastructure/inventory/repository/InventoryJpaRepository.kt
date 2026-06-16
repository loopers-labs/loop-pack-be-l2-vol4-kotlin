package com.loopers.infrastructure.inventory.repository

import com.loopers.infrastructure.inventory.entity.InventoryEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface InventoryJpaRepository : JpaRepository<InventoryEntity, Long> {
    fun findByProductId(productId: Long): InventoryEntity?

    fun findAllByProductIdIn(productIds: Collection<Long>): List<InventoryEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select inventory
        from InventoryEntity inventory
        where inventory.productId in :productIds
        order by inventory.productId asc
        """,
    )
    fun findAllByProductIdInForUpdate(
        @Param("productIds") productIds: Collection<Long>,
    ): List<InventoryEntity>
}
