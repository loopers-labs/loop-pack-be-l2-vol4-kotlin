package com.loopers.application.coupon.usecase.admin

import com.loopers.application.coupon.CouponIssueInfo
import com.loopers.application.coupon.PageResult
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

@Component
class GetCouponIssuesUsecase(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    @Transactional(readOnly = true)
    fun execute(couponId: Long, page: Int, size: Int): PageResult<CouponIssueInfo> {
        val now = ZonedDateTime.now()
        val coupon = couponRepository.findActiveById(couponId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.")
        val issues = userCouponRepository.findAllByCouponId(
            couponId = couponId,
            pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")),
        )
        return PageResult.from(issues) { CouponIssueInfo.from(userCoupon = it, coupon = coupon, now = now) }
    }
}
