package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponTemplateModel
import com.loopers.domain.coupon.CouponTemplateRepository
import org.springframework.stereotype.Repository

@Repository
class CouponTemplateRepositoryImpl(
    private val jpaRepository: CouponTemplateJpaRepository,
) : CouponTemplateRepository {

    override fun findByIdWithLock(id: Long): CouponTemplateModel? {
        return jpaRepository.findByIdWithLock(id)
    }

    override fun save(model: CouponTemplateModel): CouponTemplateModel {
        return jpaRepository.save(model)
    }
}
