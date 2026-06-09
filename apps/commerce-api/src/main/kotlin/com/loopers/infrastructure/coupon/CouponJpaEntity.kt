package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.DiscountPolicy
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "coupons")
class CouponJpaEntity(
    name: String,
    policyType: DiscountPolicy.Type,
    policyValue: Long,
) : BaseEntity() {
    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 32)
    val policyType: DiscountPolicy.Type = policyType

    @Column(name = "policy_value", nullable = false)
    val policyValue: Long = policyValue

    fun toDomain(): Coupon = Coupon(
        id = id,
        name = name,
        policy = when (policyType) {
            DiscountPolicy.Type.FIXED_AMOUNT -> DiscountPolicy.FixedAmount(policyValue)
            DiscountPolicy.Type.RATE -> DiscountPolicy.Rate(policyValue.toInt())
        },
    )

    fun updateName(coupon: Coupon) {
        this.name = coupon.name
    }

    companion object {
        fun from(coupon: Coupon): CouponJpaEntity = when (val policy = coupon.policy) {
            is DiscountPolicy.FixedAmount -> CouponJpaEntity(
                name = coupon.name,
                policyType = policy.type,
                policyValue = policy.amount,
            )
            is DiscountPolicy.Rate -> CouponJpaEntity(
                name = coupon.name,
                policyType = policy.type,
                policyValue = policy.percent.toLong(),
            )
        }
    }
}
