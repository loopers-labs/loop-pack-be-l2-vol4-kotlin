package com.loopers.infrastructure.coupon.repository

import com.loopers.domain.coupon.model.Coupon
import com.loopers.domain.coupon.repository.CouponRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun findByIdForUpdate(couponId: Long): Coupon? {
        return couponJpaRepository.findByIdForUpdate(couponId)
            ?.toDomain()
    }

    override fun update(coupon: Coupon): Coupon {
        val entity = couponJpaRepository.findByIdOrNull(coupon.id)
            ?.also { it.update(coupon) }
            ?: error("Coupon not found: ${coupon.id}")

        return couponJpaRepository.save(entity)
            .toDomain()
    }

    private fun com.loopers.infrastructure.coupon.entity.CouponEntity.toDomain(): Coupon {
        return Coupon(
            id = id,
            name = name,
            type = type,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            expiredAt = expiredAt,
            isDeleted = isDeleted,
            issueLimit = issueLimit,
            issuedCount = issuedCount,
        )
    }
}
