package com.loopers.coupon.domain

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "user_coupon",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_coupon_user_id_coupon_id", columnNames = ["user_id", "coupon_id"]),
    ],
)
class UserCoupon(
    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: Long,
    @Column(name = "coupon_id", nullable = false, updatable = false)
    val couponId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "granted_type", nullable = false, updatable = false)
    val grantedType: UserCouponGrantedType,
    // 관리자 accountId. SYSTEM 발급이면 SYSTEM_GRANTED(-1)
    @Column(name = "granted_by", nullable = false, updatable = false)
    val grantedBy: Long,
) : BaseEntity() {
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: UserCouponStatus = UserCouponStatus.AVAILABLE
        protected set

    companion object {
        const val SYSTEM_GRANTED = -1L
    }
}

enum class UserCouponStatus {
    AVAILABLE,
    USED,
}

enum class UserCouponGrantedType {
    ADMIN,
    SYSTEM,
}
