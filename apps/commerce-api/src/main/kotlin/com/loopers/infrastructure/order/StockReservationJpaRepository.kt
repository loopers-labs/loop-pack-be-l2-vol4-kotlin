package com.loopers.infrastructure.order

import com.loopers.domain.order.StockReservation
import com.loopers.domain.order.StockReservationStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockReservationJpaRepository : JpaRepository<StockReservation, Long> {
    fun findAllByOrderId(orderId: Long): List<StockReservation>

    fun findAllByOrderIdAndStatusAndDeletedAtIsNull(
        orderId: Long,
        status: StockReservationStatus,
    ): List<StockReservation>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update StockReservation reservation
           set reservation.status = :nextStatus
         where reservation.orderId = :orderId
           and reservation.status = :currentStatus
           and reservation.deletedAt is null
        """,
    )
    fun transitionByOrderId(
        @Param("orderId") orderId: Long,
        @Param("currentStatus") currentStatus: StockReservationStatus,
        @Param("nextStatus") nextStatus: StockReservationStatus,
    ): Int
}
