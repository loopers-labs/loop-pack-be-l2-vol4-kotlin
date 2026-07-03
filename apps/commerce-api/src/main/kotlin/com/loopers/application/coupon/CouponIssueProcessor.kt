package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponIssueMessage
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueStatus
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 선착순 쿠폰 발급의 핵심 처리.
 * Consumer 가 파티션당 단일 스레드로 호출하므로, 수량 제어는 "직렬화"로 이뤄진다.
 * 멱등(요청 상태) + 사용자별 유일성(UK) + 전역 수량(원자적 감소)의 세 방어선을 조합한다.
 */
@Component
class CouponIssueProcessor(
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val userCouponRepository: UserCouponRepository,
    private val couponService: CouponService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun process(message: CouponIssueMessage) {
        val request = couponIssueRequestRepository.findByRequestId(message.requestId)
            ?: run {
                log.warn("알 수 없는 발급 요청, 무시: {}", message.requestId)
                return
            }
        // 멱등: 이미 처리된 요청은 재수신되어도 다시 처리하지 않는다.
        if (request.status != CouponIssueStatus.PENDING) {
            return
        }
        // 중복 요청: 이미 보유 중이면 수량을 소모하지 않고 성공 처리.
        if (userCouponRepository.existsByUserIdAndCouponId(message.userId, message.couponId)) {
            request.markSuccess()
            return
        }
        // 전역 수량: 원자적 감소. 소진 시 선착순 마감.
        if (!couponService.tryIssue(message.couponId)) {
            request.markFailed("SOLD_OUT")
            return
        }
        userCouponRepository.save(UserCouponModel(userId = message.userId, couponId = message.couponId))
        request.markSuccess()
    }
}
