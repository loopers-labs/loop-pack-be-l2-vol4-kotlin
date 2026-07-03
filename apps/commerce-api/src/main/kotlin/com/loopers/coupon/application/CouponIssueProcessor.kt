package com.loopers.coupon.application

import com.loopers.coupon.domain.CouponErrorCode
import com.loopers.coupon.domain.CouponIssueResult
import com.loopers.coupon.domain.CouponIssueResultRepository
import com.loopers.coupon.domain.CouponIssueResultStatus
import com.loopers.coupon.infrastructure.messaging.CouponIssueRequestEvent
import com.loopers.support.error.NotFoundException
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CouponIssueProcessor(
    val couponService: CouponService,
    val couponIssueResultRepository: CouponIssueResultRepository,
) {
    // 멱등 게이트: 신규/PENDING 잔존이면 true(처리 진행), 이미 확정이면 false(재전송 skip — 중복 차감 차단)
    @Transactional
    fun register(couponIssueRequestEvent: CouponIssueRequestEvent): Boolean {
        val existing = couponIssueResultRepository.findById(couponIssueRequestEvent.requestId)
        if (existing != null) {
            return existing.status == CouponIssueResultStatus.PENDING
        }
        couponIssueResultRepository.save(
            CouponIssueResult(
                requestId = couponIssueRequestEvent.requestId,
                couponId = couponIssueRequestEvent.couponId,
                userId = couponIssueRequestEvent.userId,
                requestedAt = couponIssueRequestEvent.requestedAt,
            ),
        )
        return true
    }

    // 발급 확정과 결과 기록을 한 트랜잭션으로 — 실패 시 함께 롤백되어 "발급됐는데 기록 없음"이 불가능
    @Transactional
    fun approve(couponIssueRequestEvent: CouponIssueRequestEvent) {
        val couponIssueResult = findResult(couponIssueRequestEvent.requestId)
        val couponIssueInfo = couponService.issue(
            CouponIssueCommand(couponIssueRequestEvent.couponId, couponIssueRequestEvent.userId),
        )
        couponIssueResult.markIssued(couponIssueInfo.userCouponId, LocalDateTime.now())
    }

    @Transactional
    fun reject(requestId: String, rejectReason: String) {
        findResult(requestId).markRejected(rejectReason, LocalDateTime.now())
    }

    private fun findResult(requestId: String): CouponIssueResult =
        couponIssueResultRepository.findById(requestId)
            ?: throw NotFoundException(CouponErrorCode.ISSUE_REQUEST_NOT_FOUND)
}
