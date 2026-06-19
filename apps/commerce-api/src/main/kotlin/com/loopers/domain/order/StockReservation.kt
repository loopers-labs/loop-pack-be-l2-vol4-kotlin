package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "stock_reservations")
class StockReservation(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: StockReservationStatus = StockReservationStatus.IN_PROGRESS,
) : BaseEntity() {
    init {
        if (quantity <= 0) throw CoreException(ErrorType.BAD_REQUEST, "예약 수량은 0보다 커야 합니다.")
    }

    fun complete() {
        if (status != StockReservationStatus.IN_PROGRESS) {
            throw CoreException(ErrorType.CONFLICT, "진행 중 예약만 확정할 수 있습니다.")
        }
        status = StockReservationStatus.COMPLETED
    }

    fun confirm() {
        complete()
    }

    fun expire() {
        if (status != StockReservationStatus.IN_PROGRESS) {
            throw CoreException(ErrorType.CONFLICT, "진행 중 예약만 만료할 수 있습니다.")
        }
        status = StockReservationStatus.EXPIRED
    }

    fun cancel() {
        if (status != StockReservationStatus.IN_PROGRESS && status != StockReservationStatus.COMPLETED) {
            throw CoreException(ErrorType.CONFLICT, "진행 중 또는 확정 예약만 취소할 수 있습니다.")
        }
        status = StockReservationStatus.CANCELED
    }
}
