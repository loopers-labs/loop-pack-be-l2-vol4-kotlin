package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.UserCoupon
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "user_coupons",
    indexes = [
        Index(name = "idx_user_coupons_user_id_coupon_id", columnList = "user_id, coupon_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_coupons_user_coupon", columnNames = ["user_id", "coupon_id"]),
    ],
)
class UserCouponJpaEntity(
    userId: Long,
    couponId: Long,
    usedAt: LocalDateTime? = null,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    val userId: Long = userId

    @Column(name = "coupon_id", nullable = false)
    val couponId: Long = couponId

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = usedAt
        protected set

    fun toDomain(): UserCoupon = UserCoupon(
        id = id,
        userId = userId,
        couponId = couponId,
        usedAt = usedAt,
    )

    companion object {
        fun from(userCoupon: UserCoupon): UserCouponJpaEntity = UserCouponJpaEntity(
            userId = userCoupon.userId,
            couponId = userCoupon.couponId,
            usedAt = userCoupon.usedAt,
        )
    }
}
