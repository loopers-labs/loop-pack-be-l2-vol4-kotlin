package com.loopers.application.coupon

import com.loopers.config.kafka.Topics
import com.loopers.config.kafka.event.CouponIssueRequestMessage
import com.loopers.domain.coupon.CouponIssueRequestModel
import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.CouponIssueRequestStatus
import com.loopers.domain.coupon.CouponTemplateService
import com.loopers.infrastructure.coupon.CouponSoldOutCacheManager
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 선착순 쿠폰 비동기 발급 요청 Facade.
 * API는 요청만 Kafka에 발행하고 빠르게 응답하며,
 * 실제 발급은 commerce-streamer의 CouponIssueConsumer가 순차 처리한다.
 */
@Component
class CouponIssueFacade(
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val couponTemplateService: CouponTemplateService,
    private val couponSoldOutCacheManager: CouponSoldOutCacheManager,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {

    /**
     * 쿠폰 발급을 비동기로 요청한다.
     * 1. 소진 캐시 확인 (빠른 실패)
     * 2. 중복 요청 검증
     * 3. coupon_issue_requests 테이블에 PENDING 상태 레코드 생성
     * 4. coupon-issue-requests 토픽에 발행
     *
     * @param userId 요청 사용자 ID
     * @param couponTemplateId 발급 대상 쿠폰 템플릿 ID
     * @return 발급 요청 정보 (requestId 포함, polling 키로 사용)
     */
    @Transactional
    fun requestIssue(userId: Long, couponTemplateId: Long): CouponIssueRequestInfo {
        if (couponSoldOutCacheManager.isSoldOut(couponTemplateId)) {
            throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 수량이 모두 소진되었습니다.")
        }

        val template = couponTemplateService.getById(couponTemplateId)
        if (!template.hasRemainingQuantity()) {
            couponSoldOutCacheManager.markSoldOut(couponTemplateId)
            throw CoreException(ErrorType.BAD_REQUEST, "쿠폰 발급 수량이 모두 소진되었습니다.")
        }

        if (couponIssueRequestRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)) {
            throw CoreException(ErrorType.CONFLICT, "이미 발급 요청한 쿠폰입니다.")
        }

        val request = couponIssueRequestRepository.save(
            CouponIssueRequestModel(userId = userId, couponTemplateId = couponTemplateId),
        )

        kafkaTemplate.send(
            Topics.COUPON_ISSUE_REQUESTS,
            couponTemplateId.toString(),
            CouponIssueRequestMessage(
                requestId = request.id,
                couponId = couponTemplateId,
                userId = userId,
            ),
        )

        return CouponIssueRequestInfo.from(request)
    }

    /**
     * 발급 요청의 처리 결과를 조회한다 (polling 방식).
     *
     * @param requestId 발급 요청 ID
     * @param userId 요청 사용자 ID (본인 확인용)
     * @return 현재 처리 상태
     */
    @Transactional(readOnly = true)
    fun getRequestStatus(requestId: Long, userId: Long): CouponIssueRequestInfo {
        val request = couponIssueRequestRepository.findById(requestId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 요청입니다.")
        if (request.userId != userId) {
            throw CoreException(ErrorType.BAD_REQUEST, "본인의 요청만 조회할 수 있습니다.")
        }
        return CouponIssueRequestInfo.from(request)
    }
}

/**
 * 쿠폰 발급 요청 정보 DTO (Application Layer).
 */
data class CouponIssueRequestInfo(
    val requestId: Long,
    val couponTemplateId: Long,
    val status: CouponIssueRequestStatus,
    val failureReason: String?,
) {
    companion object {
        /** 엔티티에서 변환 */
        fun from(model: CouponIssueRequestModel) = CouponIssueRequestInfo(
            requestId = model.id,
            couponTemplateId = model.couponTemplateId,
            status = model.status,
            failureReason = model.failureReason,
        )
    }
}
