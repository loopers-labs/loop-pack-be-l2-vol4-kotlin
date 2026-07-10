package com.loopers.domain.order.infrastructure.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, Long> {
    fun findByOrderedUserIdAndIdempotencyKey(
        orderedUserId: Long,
        idempotencyKey: String,
    ): OrderJpaEntity?

    @Query(
        """
        select o from OrderJpaEntity o
        where o.orderedUserId = :orderedUserId
          and (:startAt is null or o.createdAt >= :startAt)
          and (:endAt is null or o.createdAt < :endAt)
        order by o.createdAt desc, o.id desc
        """,
    )
    fun findByOrderedUserId(
        @Param("orderedUserId") orderedUserId: Long,
        @Param("startAt") startAt: ZonedDateTime?,
        @Param("endAt") endAt: ZonedDateTime?,
    ): List<OrderJpaEntity>

    @Query("select o from OrderJpaEntity o order by o.createdAt desc, o.id desc")
    fun findAllByCreatedAtDesc(pageable: Pageable): List<OrderJpaEntity>
}
