package com.loopers.domain.coupon.application

import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.info.CouponTemplateInfo
import com.loopers.domain.coupon.application.info.IssuedCouponInfo
import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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
        CouponTemplateInfo.from(couponService.getTemplate(templateId))

    fun issue(userId: Long, templateId: Long): IssuedCouponInfo {
        val issuedCoupon = couponService.issue(userId, templateId)
        val template = findTemplate(issuedCoupon.couponTemplateId)
        return IssuedCouponInfo.from(issuedCoupon, template, LocalDateTime.now())
    }

    fun findMyCoupons(userId: Long): List<IssuedCouponInfo> {
        val issuedCoupons = couponService.findMyCoupons(userId)
        val templatesById = couponService.getTemplatesByIds(issuedCoupons.map { it.couponTemplateId }.toSet())
            .map { CouponTemplateInfo.from(it) }
            .associateBy { it.id }
        val now = LocalDateTime.now()
        return issuedCoupons.map { issuedCoupon ->
            val template = templatesById[issuedCoupon.couponTemplateId] ?: throw CoreException(ErrorType.NOT_FOUND)
            IssuedCouponInfo.from(issuedCoupon, template, now)
        }
    }

    fun findIssuedCouponsByTemplate(templateId: Long, page: Int, size: Int): List<IssuedCouponInfo> {
        val template = findTemplate(templateId)
        val now = LocalDateTime.now()
        return couponService.findIssuedCouponsByTemplate(templateId, page, size)
            .map { IssuedCouponInfo.from(it, template, now) }
    }
}
