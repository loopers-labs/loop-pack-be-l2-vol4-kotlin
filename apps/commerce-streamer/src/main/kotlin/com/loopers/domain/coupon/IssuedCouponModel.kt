package com.loopers.domain.coupon

import com.loopers.domain.BaseEntity
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
}

enum class CouponStatus {
    AVAILABLE,
    USED,
}
