package com.loopers.domain.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CouponRepository {
    fun save(coupon: CouponModel): CouponModel
    fun findActiveById(id: Long): CouponModel?
    fun findAllActive(pageable: Pageable): Page<CouponModel>
    fun findAllByIds(ids: List<Long>): List<CouponModel>

    /**
     * 발급 수량을 원자적으로 1 증가시킨다. (무제한이거나 남은 수량이 있을 때만)
     * @return true 발급 성공, false 소진(선착순 마감)
     */
    fun tryIssue(couponId: Long): Boolean
}
