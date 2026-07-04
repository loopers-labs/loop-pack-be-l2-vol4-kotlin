package com.loopers.domain.coupon.application

import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.info.CouponIssueRequestInfo
import com.loopers.domain.coupon.application.info.CouponTemplateInfo
import com.loopers.domain.coupon.application.info.IssuedCouponInfo
import com.loopers.domain.coupon.application.service.CouponIssueRequestService
import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.page.PageResult
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

@Component
class CouponFacade(
    private val couponService: CouponService,
    private val couponIssueRequestService: CouponIssueRequestService,
) {
    fun createTemplate(command: CouponTemplateCommand): CouponTemplateInfo =
        CouponTemplateInfo.from(couponService.createTemplate(command))

    fun updateTemplate(templateId: Long, command: CouponTemplateCommand): CouponTemplateInfo =
        CouponTemplateInfo.from(couponService.updateTemplate(templateId, command))

    fun deleteTemplate(templateId: Long) {
        couponService.softDeleteTemplate(templateId)
    }

    fun findTemplates(page: Int, size: Int): PageResult<CouponTemplateInfo> =
        couponService.findTemplates(page, size).map { CouponTemplateInfo.from(it) }

    fun findTemplate(templateId: Long): CouponTemplateInfo =
        CouponTemplateInfo.from(couponService.getTemplate(templateId))

    fun issue(userId: Long, templateId: Long): IssuedCouponInfo {
        val issuedCoupon = couponService.issue(userId, templateId)
        val template = findTemplate(issuedCoupon.couponTemplateId)
        return IssuedCouponInfo.from(issuedCoupon, template, LocalDateTime.now())
    }

    fun requestIssue(userId: Long, templateId: Long): CouponIssueRequestInfo =
        CouponIssueRequestInfo.from(couponIssueRequestService.requestIssue(userId, templateId))

    fun findIssueRequest(userId: Long, requestId: UUID): CouponIssueRequestInfo =
        CouponIssueRequestInfo.from(couponIssueRequestService.getRequest(userId, requestId))

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

    fun findIssuedCouponsByTemplate(templateId: Long, page: Int, size: Int): PageResult<IssuedCouponInfo> {
        val template = findTemplate(templateId)
        val now = LocalDateTime.now()
        return couponService.findIssuedCouponsByTemplate(templateId, page, size)
            .map { IssuedCouponInfo.from(it, template, now) }
    }
}
