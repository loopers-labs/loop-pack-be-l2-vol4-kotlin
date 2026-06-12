package com.loopers.domain.coupon.infrastructure.persistence.issued

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.model.IssuedCouponModel
import com.loopers.domain.coupon.model.IssuedCouponStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import java.time.LocalDateTime

const val ISSUED_COUPON_USER_TEMPLATE_UNIQUE_CONSTRAINT = "uk_issued_coupons_user_template"

@Entity
@Table(
    name = "issued_coupons",
    uniqueConstraints = [
        UniqueConstraint(
            name = ISSUED_COUPON_USER_TEMPLATE_UNIQUE_CONSTRAINT,
            columnNames = ["user_id", "coupon_template_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_issued_coupons_user_id", columnList = "user_id"),
        Index(name = "idx_issued_coupons_template_id", columnList = "coupon_template_id"),
    ],
)
class IssuedCouponJpaEntity(
    @Column(name = "coupon_template_id", nullable = false)
    var couponTemplateId: Long,
    @Column(name = "user_id", nullable = false)
    var userId: Long,
    @Column(name = "coupon_status", nullable = false)
    var couponStatus: String,
    @Column(name = "issued_at", nullable = false)
    var issuedAt: LocalDateTime,
    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,
) : BaseEntity() {
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        protected set

    fun updateFrom(issuedCoupon: IssuedCouponModel) {
        couponTemplateId = issuedCoupon.couponTemplateId
        userId = issuedCoupon.userId
        couponStatus = issuedCoupon.status.name
        issuedAt = issuedCoupon.issuedAt
        usedAt = issuedCoupon.usedAt
    }

    fun toDomain(): IssuedCouponModel = IssuedCouponModel(
        id = id,
        couponTemplateId = couponTemplateId,
        userId = userId,
        status = IssuedCouponStatus.valueOf(couponStatus),
        issuedAt = issuedAt,
        usedAt = usedAt,
    )

    companion object {
        fun fromDomain(issuedCoupon: IssuedCouponModel): IssuedCouponJpaEntity = IssuedCouponJpaEntity(
            couponTemplateId = issuedCoupon.couponTemplateId,
            userId = issuedCoupon.userId,
            couponStatus = issuedCoupon.status.name,
            issuedAt = issuedCoupon.issuedAt,
            usedAt = issuedCoupon.usedAt,
        )
    }
}
