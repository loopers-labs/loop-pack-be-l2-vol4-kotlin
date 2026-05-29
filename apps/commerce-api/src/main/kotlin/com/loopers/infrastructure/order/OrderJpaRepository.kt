package com.loopers.infrastructure.order

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    @EntityGraph(attributePaths = ["_items"])
    fun findWithItemsByIdAndDeletedAtIsNull(id: Long): OrderJpaEntity?
}
