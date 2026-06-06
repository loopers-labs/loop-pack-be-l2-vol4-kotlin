package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.UserCoupon
import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "user_coupon",
    indexes = [
        Index(name = "idx_user_coupon_user_id", columnList = "user_id"),
        Index(name = "idx_user_coupon_template_id", columnList = "coupon_template_id"),
    ],
)
class UserCouponEntity(
    @Column(name = "coupon_template_id", nullable = false)
    val couponTemplateId: Long,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: PersistedCouponStatus,

    @Column(name = "issued_at", nullable = false)
    val issuedAt: LocalDateTime,

    @Column(name = "used_at")
    var usedAt: LocalDateTime?,
) : BaseEntity() {
    fun toDomain(): UserCoupon = UserCoupon(
        id = id,
        couponTemplateId = couponTemplateId,
        userId = userId,
        status = status.toDomain(),
        issuedAt = issuedAt,
        usedAt = usedAt,
    )

    companion object {
        fun from(domain: UserCoupon): UserCouponEntity = UserCouponEntity(
            couponTemplateId = domain.couponTemplateId,
            userId = domain.userId,
            status = PersistedCouponStatus.from(domain.status),
            issuedAt = domain.issuedAt,
            usedAt = domain.usedAt,
        )
    }
}
