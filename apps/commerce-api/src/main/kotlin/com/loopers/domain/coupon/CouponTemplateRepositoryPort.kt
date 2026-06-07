package com.loopers.domain.coupon

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

interface CouponTemplateRepositoryPort {
    fun save(couponTemplate: CouponTemplate): CouponTemplate
    fun findById(id: Long): CouponTemplate?
    fun findAll(pageRequest: PageRequest): PageResult<CouponTemplate>
}
