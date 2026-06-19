package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity(name = "OrderEntity")
@Table(name = "orders")
class Order(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "reservation_expires_at", nullable = false)
    val reservationExpiresAt: LocalDateTime,

    @Column(name = "delivery_address", nullable = false, length = 500)
    val deliveryAddress: String,

    @Column(name = "delivery_request", nullable = false, length = 500)
    val deliveryRequest: String,

    @Column(name = "phone_number", nullable = false, length = 30)
    val phoneNumber: String,

    @Column(name = "coupon_id")
    val couponId: Long? = null,

    @Column(name = "total_amount", nullable = false)
    val totalAmount: Long,

    @Column(name = "discount_amount", nullable = false)
    val discountAmount: Long,

    @Column(name = "payment_amount", nullable = false)
    val paymentAmount: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    var status: OrderStatus = OrderStatus.PAYMENT_PENDING,

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    var cancelReason: OrderCancelReason? = null,
) : BaseEntity() {
    init {
        if (deliveryAddress.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "배송 주소는 비어있을 수 없습니다.")
        if (phoneNumber.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "전화번호는 비어있을 수 없습니다.")
        if (totalAmount < 0) throw CoreException(ErrorType.BAD_REQUEST, "주문 총액은 0 미만일 수 없습니다.")
        if (discountAmount < 0) throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 0 미만일 수 없습니다.")
        if (discountAmount > totalAmount) throw CoreException(ErrorType.BAD_REQUEST, "할인 금액은 주문 총액보다 클 수 없습니다.")
        if (paymentAmount != totalAmount - discountAmount) {
            throw CoreException(ErrorType.BAD_REQUEST, "최종 결제 금액이 주문 금액 스냅샷과 일치하지 않습니다.")
        }
    }

    fun complete() {
        if (status != OrderStatus.PAYMENT_PENDING && status != OrderStatus.FAILED) {
            throw CoreException(ErrorType.CONFLICT, "결제대기 또는 실패 주문만 완료할 수 있습니다.")
        }
        status = OrderStatus.COMPLETED
    }

    fun markCompletionFailed() {
        if (status != OrderStatus.PAYMENT_PENDING && status != OrderStatus.FAILED) {
            throw CoreException(ErrorType.CONFLICT, "결제대기 또는 실패 주문만 실패 처리할 수 있습니다.")
        }
        status = OrderStatus.FAILED
    }

    fun expire() {
        if (status != OrderStatus.PAYMENT_PENDING) {
            throw CoreException(ErrorType.CONFLICT, "결제대기 주문만 만료할 수 있습니다.")
        }
        status = OrderStatus.EXPIRED
    }

    fun cancelByUser() {
        when (status) {
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.COMPLETED,
            -> {
                cancelReason = OrderCancelReason.USER_REQUESTED
                status = OrderStatus.CANCELED
            }
            OrderStatus.FAILED,
            OrderStatus.EXPIRED,
            OrderStatus.CANCELED,
            OrderStatus.SHIPPING_STARTED,
            -> throw CoreException(ErrorType.CONFLICT, "취소할 수 없는 주문 상태입니다.")
        }
    }

    fun cancelByOperator(reason: OrderCancelReason) {
        if (status != OrderStatus.FAILED && status != OrderStatus.COMPLETED && status != OrderStatus.PAYMENT_PENDING) {
            throw CoreException(ErrorType.CONFLICT, "운영자 취소가 불가능한 주문 상태입니다.")
        }
        cancelReason = reason
        status = OrderStatus.CANCELED
    }

    fun cancel(reason: OrderCancelReason) {
        when (status) {
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.COMPLETED,
            -> {
                cancelReason = reason
                status = OrderStatus.CANCELED
            }
            OrderStatus.FAILED,
            OrderStatus.EXPIRED,
            OrderStatus.CANCELED,
            OrderStatus.SHIPPING_STARTED,
            -> throw CoreException(ErrorType.CONFLICT, "취소할 수 없는 주문 상태입니다.")
        }
    }

    fun startShipping() {
        if (status != OrderStatus.COMPLETED) {
            throw CoreException(ErrorType.CONFLICT, "주문완료 상태에서만 배송을 시작할 수 있습니다.")
        }
        status = OrderStatus.SHIPPING_STARTED
    }
}
