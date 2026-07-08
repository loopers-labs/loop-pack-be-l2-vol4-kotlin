package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponTemplate
import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(name = "coupon_template")
class CouponTemplateEntity(
    @Column(name = "name", nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    var type: PersistedCouponType,

    @Column(name = "discount_value", nullable = false)
    var discountValue: Long,

    @Column(name = "min_order_amount", nullable = false)
    var minOrderAmount: Long,

    @Column(name = "expired_at", nullable = false)
    var expiredAt: LocalDateTime,

    @Column(name = "total_count", nullable = false)
    val totalCount: Long,
) : BaseEntity() {
    fun update(
        name: String,
        type: PersistedCouponType,
        discountValue: Long,
        minOrderAmount: Long,
        expiredAt: LocalDateTime,
    ) {
        this.name = name
        this.type = type
        this.discountValue = discountValue
        this.minOrderAmount = minOrderAmount
        this.expiredAt = expiredAt
    }

    fun toDomain(): CouponTemplate = CouponTemplate(
        id = id,
        name = name,
        type = type.toDomain(),
        value = discountValue,
        minOrderAmount = minOrderAmount,
        expiredAt = expiredAt,
        totalCount = totalCount,
    )

    companion object {
        fun from(domain: CouponTemplate): CouponTemplateEntity = CouponTemplateEntity(
            name = domain.name,
            type = PersistedCouponType.from(domain.type),
            discountValue = domain.value,
            minOrderAmount = domain.minOrderAmount,
            expiredAt = domain.expiredAt,
            totalCount = domain.totalCount,
        )
    }
}
