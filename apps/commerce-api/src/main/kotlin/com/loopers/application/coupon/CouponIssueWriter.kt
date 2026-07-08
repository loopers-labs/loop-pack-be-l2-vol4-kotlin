package com.loopers.application.coupon

import com.loopers.application.outbox.OutboxSavedEvent
import com.loopers.application.outbox.OutboxWriter
import com.loopers.domain.coupon.CouponIssueRequest
import com.loopers.domain.coupon.CouponIssueRequestRepositoryPort
import com.loopers.domain.outbox.Outbox
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 쿠폰 발급 요청 + Outbox 저장을 단일 트랜잭션으로 처리하는 컴포넌트.
 * CouponApplicationServiceAdapter에서 직접 @Transactional을 사용할 경우
 * DataIntegrityViolationException을 catch한 후 Redis 보상이 불가능하므로 별도 Bean으로 분리한다.
 */
@Component
class CouponIssueWriter(
    private val couponIssueRequestRepositoryPort: CouponIssueRequestRepositoryPort,
    private val outboxWriter: OutboxWriter,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun saveAndPublish(request: CouponIssueRequest, outbox: Outbox): CouponIssueRequest {
        val saved = couponIssueRequestRepositoryPort.save(request)
        outboxWriter.save(outbox)
        applicationEventPublisher.publishEvent(OutboxSavedEvent(outbox))
        return saved
    }
}
