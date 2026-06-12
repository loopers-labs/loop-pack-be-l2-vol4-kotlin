package com.loopers.application.coupon.usecase.admin

import com.loopers.application.coupon.AdminCouponInfo
import com.loopers.application.coupon.PageResult
import com.loopers.domain.coupon.CouponRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class GetCouponsUsecase(
    private val couponRepository: CouponRepository,
) {
    @Transactional(readOnly = true)
    fun execute(page: Int, size: Int): PageResult<AdminCouponInfo> {
        val coupons = couponRepository.findAllActive(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")))
        return PageResult.from(coupons) { AdminCouponInfo.from(it) }
    }
}
