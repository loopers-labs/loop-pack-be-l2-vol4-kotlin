package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.ZonedDateTime

@Entity
@Table(name = "issued_coupons")
class IssuedCouponModel(
    couponTemplateId: Long,
    userId: Long,
) : BaseEntity() {

    @Column(name = "coupon_template_id", nullable = false)
    var couponTemplateId: Long = couponTemplateId
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CouponStatus = CouponStatus.AVAILABLE
        protected set

    @Column(name = "used_at")
    var usedAt: ZonedDateTime? = null
        protected set

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    fun use(now: ZonedDateTime = ZonedDateTime.now()) {
        if (status != CouponStatus.AVAILABLE) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "사용할 수 없는 쿠폰입니다. 현재 상태: $status",
            )
        }
        status = CouponStatus.USED
        usedAt = now
    }

    fun restoreUsage() {
        if (status != CouponStatus.USED) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "사용된 쿠폰만 복원할 수 있습니다. 현재 상태: $status",
            )
        }
        status = CouponStatus.AVAILABLE
        usedAt = null
    }

    fun isUsable(): Boolean = status == CouponStatus.AVAILABLE

    fun validateOwner(userId: Long) {
        if (this.userId != userId) {
            throw CoreException(
                errorType = ErrorType.BAD_REQUEST,
                customMessage = "본인의 쿠폰만 사용할 수 있습니다.",
            )
        }
    }
}
