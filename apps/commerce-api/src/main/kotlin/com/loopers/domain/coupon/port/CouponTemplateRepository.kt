package com.loopers.domain.coupon.port

import com.loopers.domain.coupon.model.CouponTemplateModel
import com.loopers.support.page.PageResult

interface CouponTemplateRepository {
    fun save(template: CouponTemplateModel): CouponTemplateModel

    fun findByIdOrNull(templateId: Long): CouponTemplateModel?

    fun findByIdForUpdateOrNull(templateId: Long): CouponTemplateModel?

    fun findAllByIds(templateIds: Set<Long>): List<CouponTemplateModel>

    fun findAll(page: Int, size: Int): PageResult<CouponTemplateModel>
}
