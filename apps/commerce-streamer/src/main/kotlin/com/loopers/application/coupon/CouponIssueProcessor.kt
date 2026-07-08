package com.loopers.application.coupon

import com.loopers.infrastructure.coupon.CouponIssueRequestStreamerJpaRepository
import com.loopers.infrastructure.coupon.PersistedStreamerCouponIssueFailureReason
import com.loopers.infrastructure.coupon.PersistedStreamerCouponIssueStatus
import com.loopers.infrastructure.coupon.UserCouponStreamerEntity
import com.loopers.infrastructure.coupon.UserCouponStreamerJpaRepository
import com.loopers.infrastructure.metric.EventHandledEntity
import com.loopers.infrastructure.metric.EventHandledJpaRepository
import com.loopers.interfaces.consumer.CouponIssuePayload
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class CouponIssueProcessor(
    private val couponIssueRequestJpaRepository: CouponIssueRequestStreamerJpaRepository,
    private val userCouponJpaRepository: UserCouponStreamerJpaRepository,
    private val eventHandledJpaRepository: EventHandledJpaRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun process(payload: CouponIssuePayload) {
        if (eventHandledJpaRepository.existsById(payload.eventId)) return

        try {
            userCouponJpaRepository.save(
                UserCouponStreamerEntity(
                    userId = payload.userId,
                    couponTemplateId = payload.couponTemplateId,
                ),
            )
            couponIssueRequestJpaRepository.updateStatus(
                userId = payload.userId,
                couponTemplateId = payload.couponTemplateId,
                status = PersistedStreamerCouponIssueStatus.COMPLETED,
                failureReason = null,
            )
        } catch (e: DataIntegrityViolationException) {
            log.warn(
                "쿠폰 중복 발급 감지 userId={} couponTemplateId={}",
                payload.userId,
                payload.couponTemplateId,
            )
            couponIssueRequestJpaRepository.updateStatus(
                userId = payload.userId,
                couponTemplateId = payload.couponTemplateId,
                status = PersistedStreamerCouponIssueStatus.FAILED,
                failureReason = PersistedStreamerCouponIssueFailureReason.DUPLICATE,
            )
        } catch (e: Exception) {
            log.error(
                "쿠폰 발급 처리 실패 userId={} couponTemplateId={}",
                payload.userId,
                payload.couponTemplateId,
                e,
            )
            couponIssueRequestJpaRepository.updateStatus(
                userId = payload.userId,
                couponTemplateId = payload.couponTemplateId,
                status = PersistedStreamerCouponIssueStatus.FAILED,
                failureReason = PersistedStreamerCouponIssueFailureReason.SYSTEM_ERROR,
            )
        }

        eventHandledJpaRepository.save(EventHandledEntity(eventId = payload.eventId))
    }
}
