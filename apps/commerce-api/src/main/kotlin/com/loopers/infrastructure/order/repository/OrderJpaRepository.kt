package com.loopers.infrastructure.order.repository

import com.loopers.infrastructure.order.entity.OrderEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    @EntityGraph(attributePaths = ["items"])
    fun findAllByMemberIdAndOrderedAtBetweenOrderByOrderedAtDescIdDesc(
        memberId: Long,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
    ): List<OrderEntity>

    @EntityGraph(attributePaths = ["items"])
    fun findByMemberIdAndId(memberId: Long, orderId: Long): OrderEntity?

    @EntityGraph(attributePaths = ["items"])
    @Query("select orders from OrderEntity orders where orders.id = :orderId")
    fun findWithItemsById(@Param("orderId") orderId: Long): OrderEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = ["items"])
    @Query("select orders from OrderEntity orders where orders.id = :orderId")
    fun findByIdForUpdate(@Param("orderId") orderId: Long): OrderEntity?
}
