package com.loopers.domain.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CouponRepository {
    fun save(coupon: CouponModel): CouponModel
    fun findActiveById(id: Long): CouponModel?
    fun findAllActive(pageable: Pageable): Page<CouponModel>

    /** 발급 쿠폰 표시용 — 삭제된 템플릿도 포함해 조회한다. */
    fun findAllByIdIn(ids: List<Long>): List<CouponModel>

    /** 선착순 슬롯 확보: issued_count < total_quantity 일 때만 원자적으로 +1. 성공 시 true. */
    fun claimIssueSlot(couponId: Long): Boolean
}
