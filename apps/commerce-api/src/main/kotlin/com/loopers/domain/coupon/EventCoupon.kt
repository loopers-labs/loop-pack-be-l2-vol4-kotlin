package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.PrimaryKeyJoinColumn
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "event_coupons")
@PrimaryKeyJoinColumn(name = "coupon_id")
@DiscriminatorValue("FIRST_COME_FIRST_SERVED")
class EventCoupon(
    name: String,
    type: CouponType,
    value: Long,
    minOrderAmount: Long? = null,
    expiredAt: LocalDateTime,

    @Column(name = "event_id", nullable = false)
    val eventId: Long,

    @Column(name = "total_quantity", nullable = false)
    val totalQuantity: Long,

    @Column(name = "issued_quantity", nullable = false)
    var issuedQuantity: Long = 0,
) : Coupon(
    name = name,
    type = type,
    value = value,
    minOrderAmount = minOrderAmount,
    expiredAt = expiredAt,
) {
    val remainingQuantity: Long
        get() = totalQuantity - issuedQuantity

    init {
        validateEventCoupon()
    }

    override fun guard() {
        super.guard()
        validateEventCoupon()
    }

    override fun getIssueType(): CouponIssueType = CouponIssueType.FIRST_COME_FIRST_SERVED

    fun isExhausted(): Boolean = issuedQuantity >= totalQuantity

    private fun validateEventCoupon() {
        if (eventId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "이벤트 ID는 0보다 커야 합니다.")
        if (totalQuantity <= 0) throw CoreException(ErrorType.BAD_REQUEST, "선착순 쿠폰 수량은 0보다 커야 합니다.")
        if (issuedQuantity < 0) throw CoreException(ErrorType.BAD_REQUEST, "발급 수량은 0 미만일 수 없습니다.")
        if (issuedQuantity > totalQuantity) throw CoreException(ErrorType.BAD_REQUEST, "발급 수량은 전체 수량보다 클 수 없습니다.")
    }
}
