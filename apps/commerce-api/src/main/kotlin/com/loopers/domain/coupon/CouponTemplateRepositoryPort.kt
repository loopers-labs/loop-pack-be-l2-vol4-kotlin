package com.loopers.domain.coupon

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult

interface CouponTemplateRepositoryPort {
    fun save(couponTemplate: CouponTemplate): CouponTemplate
    fun findById(id: Long): CouponTemplate?
    fun findAll(pageRequest: PageRequest): PageResult<CouponTemplate>

    /** 템플릿을 삭제하고, 실제로 삭제된 row 수를 반환한다(0이면 미존재 또는 이미 삭제됨). */
    fun delete(id: Long): Int
}
