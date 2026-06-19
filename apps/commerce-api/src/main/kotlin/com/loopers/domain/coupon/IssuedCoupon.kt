package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "issued_coupons",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_issued_coupons_user_coupon", columnNames = ["user_id", "coupon_id"]),
    ],
)
class IssuedCoupon(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "coupon_id", nullable = false)
    val couponId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: IssuedCouponStatus = IssuedCouponStatus.AVAILABLE,
) : BaseEntity() {
    init {
        if (userId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 0보다 커야 합니다.")
        if (couponId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 0보다 커야 합니다.")
    }

    fun markUsed() {
        if (status != IssuedCouponStatus.AVAILABLE) {
            throw CoreException(ErrorType.CONFLICT, "사용할 수 없는 쿠폰입니다.")
        }
        status = IssuedCouponStatus.USED
    }

    fun effectiveStatus(coupon: Coupon, now: LocalDateTime): IssuedCouponStatus =
        if (status == IssuedCouponStatus.AVAILABLE && coupon.isExpired(now)) {
            IssuedCouponStatus.EXPIRED
        } else {
            status
        }
}
