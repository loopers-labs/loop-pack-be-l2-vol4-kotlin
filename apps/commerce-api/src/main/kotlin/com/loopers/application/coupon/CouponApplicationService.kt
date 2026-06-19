package com.loopers.application.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponCommand
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.IssuedCoupon
import com.loopers.domain.coupon.IssuedCouponRepository
import com.loopers.domain.coupon.IssuedCouponStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class CouponApplicationService(
    private val couponRepository: CouponRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
) {
    @Transactional
    fun create(command: CouponCommand.Create): CouponInfo.Template =
        couponRepository.save(
            Coupon(
                name = command.name,
                type = command.type,
                value = command.value,
                minOrderAmount = command.minOrderAmount,
                expiredAt = command.expiredAt,
            ),
        ).let(CouponInfo.Template::from)

    @Transactional
    fun update(couponId: Long, command: CouponCommand.Update): CouponInfo.Template {
        val coupon = getCoupon(couponId)
        coupon.change(
            name = command.name,
            type = command.type,
            value = command.value,
            minOrderAmount = command.minOrderAmount,
            expiredAt = command.expiredAt,
        )
        return CouponInfo.Template.from(couponRepository.save(coupon))
    }

    @Transactional
    fun delete(couponId: Long) {
        val coupon = getCoupon(couponId)
        coupon.delete()
        couponRepository.save(coupon)
    }

    @Transactional(readOnly = true)
    fun get(couponId: Long): CouponInfo.Template =
        CouponInfo.Template.from(getCoupon(couponId))

    @Transactional(readOnly = true)
    fun getAll(page: Int, size: Int): List<CouponInfo.Template> {
        validatePage(page, size)
        return couponRepository.findAll(page, size).map(CouponInfo.Template::from)
    }

    @Transactional
    fun issue(userId: Long, couponId: Long, now: LocalDateTime = LocalDateTime.now()): CouponInfo.Issued {
        val coupon = getCoupon(couponId)
        if (coupon.isExpired(now)) {
            throw CoreException(ErrorType.CONFLICT, "만료된 쿠폰은 발급할 수 없습니다.")
        }
        if (issuedCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw CoreException(ErrorType.CONFLICT, "이미 발급된 쿠폰입니다.")
        }
        val issue = issuedCouponRepository.save(IssuedCoupon(userId = userId, couponId = couponId))
        return CouponInfo.Issued.from(issue, coupon, now)
    }

    @Transactional(readOnly = true)
    fun getMyCoupons(userId: Long, now: LocalDateTime = LocalDateTime.now()): List<CouponInfo.Issued> =
        issuedCouponRepository.findByUserId(userId)
            .map { issue -> CouponInfo.Issued.from(issue, getCoupon(issue.couponId), now) }

    @Transactional(readOnly = true)
    fun getIssues(couponId: Long, page: Int, size: Int, now: LocalDateTime = LocalDateTime.now()): List<CouponInfo.Issued> {
        validatePage(page, size)
        val coupon = getCoupon(couponId)
        return issuedCouponRepository.findByCouponId(couponId, page, size)
            .map { issue -> CouponInfo.Issued.from(issue, coupon, now) }
    }

    @Transactional
    fun useOwnedCoupon(
        userId: Long,
        couponId: Long,
        orderAmount: Long,
        now: LocalDateTime = LocalDateTime.now(),
    ): CouponInfo.Applied {
        val issue = issuedCouponRepository.findByUserIdAndCouponId(userId, couponId)
            ?: throw CoreException(ErrorType.CONFLICT, "사용할 수 없는 쿠폰입니다.")
        val coupon = getCoupon(couponId)
        if (issue.status != IssuedCouponStatus.AVAILABLE || coupon.isExpired(now)) {
            throw CoreException(ErrorType.CONFLICT, "사용할 수 없는 쿠폰입니다.")
        }
        val discountAmount = coupon.calculateDiscount(orderAmount)
        if (!issuedCouponRepository.markUsedIfAvailable(userId, couponId)) {
            throw CoreException(ErrorType.CONFLICT, "이미 사용된 쿠폰입니다.")
        }
        return CouponInfo.Applied(
            couponId = coupon.id,
            name = coupon.name,
            type = coupon.type,
            totalAmount = orderAmount,
            discountAmount = discountAmount,
            paymentAmount = orderAmount - discountAmount,
        )
    }

    private fun getCoupon(couponId: Long): Coupon =
        couponRepository.findById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")

    private fun validatePage(page: Int, size: Int) {
        if (page < 0 || size <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "페이지 요청이 올바르지 않습니다.")
        }
    }
}
