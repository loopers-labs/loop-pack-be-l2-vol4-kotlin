package com.loopers.domain.coupon.port

import com.loopers.domain.coupon.model.CouponTemplateModel

interface CouponTemplateRepository {
    fun save(template: CouponTemplateModel): CouponTemplateModel

    fun findByIdOrNull(templateId: Long): CouponTemplateModel?

    fun findAllByIds(templateIds: Set<Long>): List<CouponTemplateModel>

    fun findAll(page: Int, size: Int): List<CouponTemplateModel>
}
