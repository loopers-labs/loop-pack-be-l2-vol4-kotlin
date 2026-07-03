package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderStatus
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    @EntityGraph(attributePaths = ["_items"])
    fun findWithItemsByIdAndDeletedAtIsNull(id: Long): OrderJpaEntity?

    @EntityGraph(attributePaths = ["_items"])
    fun findByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
        status: OrderStatus,
        createdAt: ZonedDateTime,
    ): List<OrderJpaEntity>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update OrderJpaEntity o
        set o.status = :targetStatus
        where o.id = :id
          and o.status = :currentStatus
          and o.deletedAt is null
        """,
    )
    fun updateStatusIfCurrent(
        @Param("id") id: Long,
        @Param("currentStatus") currentStatus: OrderStatus,
        @Param("targetStatus") targetStatus: OrderStatus,
    ): Int
}
