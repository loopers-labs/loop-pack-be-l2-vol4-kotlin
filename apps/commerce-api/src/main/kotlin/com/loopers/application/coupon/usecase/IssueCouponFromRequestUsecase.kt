package com.loopers.application.coupon.usecase

import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class IssueCouponFromRequestUsecase(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val requestRepository: CouponIssueRequestRepository,
) {
    @Transactional
    fun issue(requestId: String, userId: Long, couponId: Long) {
        val request = requestRepository.findByRequestId(requestId) ?: return
        if (request.isTerminal()) return // 멱등: 이미 처리된 요청 재수신

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            request.markIssued() // 이미 보유 — 멱등 종결
            return
        }
        if (!couponRepository.claimIssueSlot(couponId)) {
            // claimIssueSlot 은 @Modifying(clearAutomatically = true) — 영속성 컨텍스트가 비워져 위에서 조회한
            // request 는 detach 된다. 재조회한 관리 상태 엔티티에 반영해야 커밋 시 실제로 flush 된다.
            requestRepository.findByRequestId(requestId)?.markRejected("SOLD_OUT")
            return
        }
        val claimedRequest = requestRepository.findByRequestId(requestId) ?: return
        try {
            userCouponRepository.save(UserCouponModel(userId = userId, couponId = couponId))
            claimedRequest.markIssued()
        } catch (e: DataIntegrityViolationException) {
            // 사전 체크를 통과한 동시 중복 — 슬롯은 이미 차감됨(과다 차감 감수), 요청은 보유로 종결
            claimedRequest.markIssued()
        }
    }
}
