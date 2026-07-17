package com.loopers.domain.coupon

import com.loopers.config.kafka.event.CouponIssueRequestMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 선착순 쿠폰 발급 처리기.
 * Consumer로부터 위임받아 실제 발급 로직을 수행한다.
 *
 * 동시성 제어 전략:
 * 1. Kafka 파티션 키 = couponId → 같은 쿠폰 요청은 단일 파티션에서 순차 소비
 * 2. coupon_templates 비관적 락 → 수량 차감 원자성 보장
 * 3. issued_coupons 중복 검증 → 동일 사용자 중복 발급 방지
 */
@Component
class CouponIssueProcessor(
    private val couponTemplateRepository: CouponTemplateRepository,
    private val issuedCouponRepository: IssuedCouponRepository,
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 쿠폰 발급 요청을 처리한다.
     * 실패 시 request 상태를 FAILED로 전이하고 사유를 기록한다.
     *
     * @param message Kafka에서 수신한 발급 요청 메시지
     */
    @Transactional
    fun process(message: CouponIssueRequestMessage) {
        val request = couponIssueRequestRepository.findById(message.requestId)
        if (request == null) {
            log.warn("[쿠폰발급] 요청을 찾을 수 없음 (requestId={})", message.requestId)
            return
        }

        if (request.status != CouponIssueRequestStatus.PENDING) {
            log.info("[쿠폰발급] 이미 처리된 요청 skip (requestId={}, status={})", message.requestId, request.status)
            return
        }

        if (issuedCouponRepository.existsByUserIdAndCouponTemplateId(message.userId, message.couponId)) {
            request.markFailed("이미 발급된 쿠폰입니다.")
            couponIssueRequestRepository.save(request)
            log.info("[쿠폰발급] 중복 발급 방지 (userId={}, couponId={})", message.userId, message.couponId)
            return
        }

        val template = couponTemplateRepository.findByIdWithLock(message.couponId)
        if (template == null) {
            request.markFailed("존재하지 않는 쿠폰입니다.")
            couponIssueRequestRepository.save(request)
            return
        }

        if (!template.hasRemainingQuantity()) {
            request.markFailed("쿠폰 발급 수량이 모두 소진되었습니다.")
            couponIssueRequestRepository.save(request)
            log.info("[쿠폰발급] 수량 소진 (couponId={})", message.couponId)
            return
        }

        template.issueOne()
        couponTemplateRepository.save(template)

        issuedCouponRepository.save(
            IssuedCouponModel(couponTemplateId = message.couponId, userId = message.userId),
        )

        request.markSuccess()
        couponIssueRequestRepository.save(request)
        log.info("[쿠폰발급] 발급 완료 (requestId={}, userId={}, couponId={})", message.requestId, message.userId, message.couponId)
    }
}
