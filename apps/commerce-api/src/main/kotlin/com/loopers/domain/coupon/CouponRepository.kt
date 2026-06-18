package com.loopers.domain.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CouponRepository {
    fun save(coupon: CouponModel): CouponModel
    fun findActiveById(id: Long): CouponModel?
    fun findAllActive(pageable: Pageable): Page<CouponModel>

    /** 발급 쿠폰 표시용 — 삭제된 템플릿도 포함해 조회한다. */
    fun findAllByIdIn(ids: List<Long>): List<CouponModel>
}
