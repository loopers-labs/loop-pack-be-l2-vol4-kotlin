package com.loopers.domain.coupon.port

import com.loopers.domain.coupon.model.CouponTemplateModel

interface CouponTemplateRepository {
    fun save(template: CouponTemplateModel): CouponTemplateModel

    fun findById(templateId: Long): CouponTemplateModel?

    fun findAll(page: Int, size: Int): List<CouponTemplateModel>
}
