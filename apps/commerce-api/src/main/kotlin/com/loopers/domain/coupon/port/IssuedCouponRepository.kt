package com.loopers.domain.coupon.port

import com.loopers.domain.coupon.model.IssuedCouponModel
import com.loopers.support.page.PageResult

interface IssuedCouponRepository {
    fun save(issuedCoupon: IssuedCouponModel): IssuedCouponModel

    fun existsByUserIdAndTemplateId(userId: Long, templateId: Long): Boolean

    fun findByIdOrNull(issuedCouponId: Long): IssuedCouponModel?

    fun findByUserId(userId: Long): List<IssuedCouponModel>

    fun findByTemplateId(templateId: Long, page: Int, size: Int): PageResult<IssuedCouponModel>
}
