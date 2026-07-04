package com.loopers.domain.coupon.infrastructure.persistence.template

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.model.CouponTemplateModel
import com.loopers.domain.coupon.vo.CouponName
import com.loopers.domain.coupon.vo.CouponType
import com.loopers.domain.coupon.vo.FixedAmountDiscountPolicy
import com.loopers.domain.coupon.vo.PercentageDiscountPolicy
import com.loopers.domain.product.vo.Money
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "coupon_templates")
class CouponTemplateJpaEntity(
    @Column(name = "coupon_name", nullable = false)
    var couponName: String,
    @Column(name = "coupon_type", nullable = false)
    var couponType: String,
    @Column(name = "discount_value", nullable = false)
    var discountValue: Long,
    @Column(name = "min_order_amount", nullable = false)
    var minOrderAmount: Long,
    @Column(name = "expired_at", nullable = false)
    var expiredAt: LocalDateTime,
    @Column(name = "total_quantity", nullable = false)
    var totalQuantity: Long = Long.MAX_VALUE,
    @Column(name = "issued_quantity", nullable = false)
    var issuedQuantity: Long = 0,
) : BaseEntity() {
    fun updateFrom(template: CouponTemplateModel) {
        couponName = template.name.value
        val mappedPolicy = template.discountPolicy.toStorageValue()
        couponType = mappedPolicy.type.name
        discountValue = mappedPolicy.value
        minOrderAmount = template.minOrderAmount.value
        expiredAt = template.expiredAt
        totalQuantity = template.totalQuantity
        issuedQuantity = template.issuedQuantity
        if (template.deletedAt == null) {
            restore()
        } else {
            delete()
        }
    }

    fun toDomain(): CouponTemplateModel = CouponTemplateModel(
        id = id,
        name = CouponName.of(couponName),
        discountPolicy = when (CouponType.valueOf(couponType)) {
            CouponType.FIXED_AMOUNT -> FixedAmountDiscountPolicy.of(Money.of(discountValue))
            CouponType.PERCENTAGE -> PercentageDiscountPolicy.of(discountValue.toInt())
        },
        minOrderAmount = Money.of(minOrderAmount),
        expiredAt = expiredAt,
        totalQuantity = totalQuantity,
        issuedQuantity = issuedQuantity,
        deletedAt = deletedAt?.toLocalDateTime(),
    )

    companion object {
        fun fromDomain(template: CouponTemplateModel): CouponTemplateJpaEntity {
            val mappedPolicy = template.discountPolicy.toStorageValue()
            return CouponTemplateJpaEntity(
                couponName = template.name.value,
                couponType = mappedPolicy.type.name,
                discountValue = mappedPolicy.value,
                minOrderAmount = template.minOrderAmount.value,
                expiredAt = template.expiredAt,
                totalQuantity = template.totalQuantity,
                issuedQuantity = template.issuedQuantity,
            )
        }

        private fun com.loopers.domain.coupon.vo.DiscountPolicy.toStorageValue(): StorageDiscountPolicy =
            when (this) {
                is FixedAmountDiscountPolicy -> StorageDiscountPolicy(CouponType.FIXED_AMOUNT, amount.value)
                is PercentageDiscountPolicy -> StorageDiscountPolicy(CouponType.PERCENTAGE, percent.toLong())
            }
    }
}
