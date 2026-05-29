package com.loopers.infrastructure.order

import com.loopers.domain.order.StockReservation
import com.loopers.domain.order.StockReservationStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockReservationJpaRepository : JpaRepository<StockReservation, Long> {
    fun findAllByOrderId(orderId: Long): List<StockReservation>

    @Query(
        """
        select coalesce(sum(reservation.quantity), 0)
          from StockReservation reservation
         where reservation.productId = :productId
           and reservation.status = :status
           and reservation.deletedAt is null
        """,
    )
    fun sumQuantityByProductIdAndStatus(
        @Param("productId") productId: Long,
        @Param("status") status: StockReservationStatus,
    ): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select reservation
          from StockReservation reservation
         where reservation.productId = :productId
           and reservation.status = :status
           and reservation.deletedAt is null
        """,
    )
    fun findAllByProductIdAndStatusForUpdate(
        @Param("productId") productId: Long,
        @Param("status") status: StockReservationStatus,
    ): List<StockReservation>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update StockReservation reservation
           set reservation.status = 'CANCELED'
         where reservation.orderId = :orderId
           and reservation.status = 'ACTIVE'
           and reservation.deletedAt is null
        """,
    )
    fun cancelActiveByOrderId(@Param("orderId") orderId: Long): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update StockReservation reservation
           set reservation.status = 'CONFIRMED'
         where reservation.orderId = :orderId
           and reservation.status = 'ACTIVE'
           and reservation.deletedAt is null
        """,
    )
    fun confirmActiveByOrderId(@Param("orderId") orderId: Long): Int
}
