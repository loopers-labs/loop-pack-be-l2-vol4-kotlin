package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponIssueMessage
import com.loopers.domain.coupon.CouponIssueRequestService
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 선착순 쿠폰 발급의 요청 접수(Producer)와 결과 조회.
 * 발급 요청을 PENDING 으로 남기고 Kafka 에 발행만 한 뒤 즉시 requestId 를 반환한다(빠른 응답).
 * 실제 발급은 Consumer 가 순차 처리한다.
 */
@Component
class CouponIssueFacade(
    private val userService: UserService,
    private val couponService: CouponService,
    private val couponIssueRequestService: CouponIssueRequestService,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun requestIssue(loginId: String, rawPassword: String, couponId: Long): String {
        val user = userService.authenticate(loginId, rawPassword)
        couponService.getById(couponId) // 존재 검증 (없으면 NOT_FOUND)

        val requestId = UUID.randomUUID().toString()
        couponIssueRequestService.create(requestId, user.id, couponId)

        try {
            kafkaTemplate.send(TOPIC_COUPON_ISSUE, couponId.toString(), CouponIssueMessage(requestId, user.id, couponId))
                .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            log.error("쿠폰 발급 요청 발행 실패: requestId={}", requestId, e)
            couponIssueRequestService.markFailed(requestId, "PUBLISH_FAILED")
            throw CoreException(ErrorType.INTERNAL_ERROR, "발급 요청 처리에 실패했습니다.")
        }
        return requestId
    }

    fun getResult(requestId: String): CouponIssueResultInfo =
        CouponIssueResultInfo.from(couponIssueRequestService.getByRequestId(requestId))

    companion object {
        const val TOPIC_COUPON_ISSUE = "coupon-issue-requests"
        private const val SEND_TIMEOUT_SECONDS = 5L
    }
}
