package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.UserCoupon
import java.time.LocalDateTime

/**
 * 어드민 발급 내역 조회 전용 결과. 발급 쿠폰([UserCoupon])에 발급받은 사용자의 [loginId] 를 매핑한다.
 * [status] 는 DB 저장 상태를 그대로 노출한다(조회 시점 만료 반영 없음 — 관리자에게 실제 저장 상태를 보여준다).
 */
data class CouponIssueResult(
    val id: Long,
    val loginId: String,
    val status: CouponStatus,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime?,
) {
    companion object {
        fun of(userCoupon: UserCoupon, loginId: String): CouponIssueResult = CouponIssueResult(
            id = userCoupon.id,
            loginId = loginId,
            status = userCoupon.status,
            issuedAt = userCoupon.issuedAt,
            usedAt = userCoupon.usedAt,
        )
    }
}
