package com.loopers.domain.coupon.application

import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.info.CouponTemplateInfo
import com.loopers.domain.coupon.application.info.IssuedCouponInfo
import com.loopers.domain.coupon.application.service.CouponService
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class CouponFacade(
    private val couponService: CouponService,
) {
    fun createTemplate(command: CouponTemplateCommand): CouponTemplateInfo =
        CouponTemplateInfo.from(couponService.createTemplate(command))

    fun updateTemplate(templateId: Long, command: CouponTemplateCommand): CouponTemplateInfo =
        CouponTemplateInfo.from(couponService.updateTemplate(templateId, command))

    fun deleteTemplate(templateId: Long) {
        couponService.softDeleteTemplate(templateId)
    }

    fun findTemplates(page: Int, size: Int): List<CouponTemplateInfo> =
        couponService.findTemplates(page, size).map { CouponTemplateInfo.from(it) }

    fun findTemplate(templateId: Long): CouponTemplateInfo =
        CouponTemplateInfo.from(couponService.findTemplate(templateId))

    fun issue(userId: Long, templateId: Long): IssuedCouponInfo {
        val issuedCoupon = couponService.issue(userId, templateId)
        val template = findTemplate(issuedCoupon.couponTemplateId)
        return IssuedCouponInfo.from(issuedCoupon, template, LocalDateTime.now())
    }

    fun findMyCoupons(userId: Long): List<IssuedCouponInfo> =
        couponService.findMyCoupons(userId).map { issuedCoupon ->
            val template = findTemplate(issuedCoupon.couponTemplateId)
            IssuedCouponInfo.from(issuedCoupon, template, LocalDateTime.now())
        }

    fun findIssuedCouponsByTemplate(templateId: Long, page: Int, size: Int): List<IssuedCouponInfo> {
        val template = findTemplate(templateId)
        return couponService.findIssuedCouponsByTemplate(templateId, page, size)
            .map { IssuedCouponInfo.from(it, template, LocalDateTime.now()) }
    }
}
