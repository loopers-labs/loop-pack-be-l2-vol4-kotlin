package com.loopers.application.coupon

import com.loopers.domain.auth.AuthService
import com.loopers.domain.common.PageRequest
import com.loopers.domain.common.PageResult
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.domain.coupon.UserCouponService
import com.loopers.interfaces.api.coupon.CouponAdminApplicationServicePort
import com.loopers.interfaces.api.coupon.CouponApplicationServicePort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class CouponApplicationServiceAdapter(
    private val couponTemplateService: CouponTemplateService,
    private val userCouponService: UserCouponService,
    private val authService: AuthService,
) : CouponAdminApplicationServicePort,
    CouponApplicationServicePort {
    @Transactional
    override fun issueCoupon(userId: Long, couponId: Long): UserCouponResult {
        val template = couponTemplateService.getById(couponId)
        val userCoupon = userCouponService.issue(template, userId, LocalDateTime.now())
        return UserCouponResult.from(userCoupon)
    }

    @Transactional(readOnly = true)
    override fun getMyCoupons(userId: Long): List<MyCouponResult> {
        val userCoupons = userCouponService.getByUserId(userId)
        if (userCoupons.isEmpty()) return emptyList()

        val now = LocalDateTime.now()
        val templates = couponTemplateService.getByIds(userCoupons.map { it.couponTemplateId }.toSet())
        return userCoupons.mapNotNull { userCoupon ->
            val template = templates[userCoupon.couponTemplateId] ?: return@mapNotNull null
            MyCouponResult.of(userCoupon, template, now)
        }
    }

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

    @Transactional
    override fun deleteCoupon(id: Long) {
        couponTemplateService.delete(id)
    }

    @Transactional(readOnly = true)
    override fun getCouponIssues(couponId: Long, pageRequest: PageRequest): PageResult<CouponIssueResult> {
        couponTemplateService.getById(couponId)
        val page = userCouponService.getByCouponTemplateId(couponId, pageRequest)
        val loginIds = authService.findLoginIdsByUserIds(page.items.map { it.userId }.distinct())
        val items = page.items.map { CouponIssueResult.of(it, loginIds[it.userId].orEmpty()) }
        return PageResult.of(items = items, pageRequest = pageRequest, totalElements = page.totalElements)
    }
}
