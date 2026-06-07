package com.loopers.application.coupon

import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.interfaces.api.coupon.CouponAdminApplicationServicePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponApplicationServiceAdapter(
    private val couponTemplateService: CouponTemplateService,
) : CouponAdminApplicationServicePort {
    @Transactional
    override fun createCoupon(command: CreateCouponCommand): CouponResult {
        val couponTemplate = couponTemplateService.create(
            name = command.name,
            type = command.type,
            value = command.value,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
        )
        return CouponResult.from(couponTemplate)
    }

    @Transactional(readOnly = true)
    override fun getCoupons(pageRequest: PageRequest): PageResult<CouponResult> {
        val page = couponTemplateService.getAll(pageRequest)
        return PageResult.of(
            items = page.items.map { CouponResult.from(it) },
            pageRequest = pageRequest,
            totalElements = page.totalElements,
        )
    }

    @Transactional(readOnly = true)
    override fun getCoupon(id: Long): CouponResult {
        val couponTemplate = couponTemplateService.getById(id)
        return CouponResult.from(couponTemplate)
    }

    @Transactional
    override fun updateCoupon(command: UpdateCouponCommand): CouponResult {
        val couponTemplate = couponTemplateService.update(
            id = command.id,
            name = command.name,
            type = command.type,
            value = command.value,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
        )
        return CouponResult.from(couponTemplate)
    }
}
