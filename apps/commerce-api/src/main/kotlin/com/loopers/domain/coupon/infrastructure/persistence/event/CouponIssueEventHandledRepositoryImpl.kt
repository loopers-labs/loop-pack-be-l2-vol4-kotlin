package com.loopers.domain.coupon.infrastructure.persistence.event

import com.loopers.domain.coupon.port.CouponIssueEventHandledRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CouponIssueEventHandledRepositoryImpl(
    private val couponIssueEventHandledJpaRepository: CouponIssueEventHandledJpaRepository,
) : CouponIssueEventHandledRepository {
    override fun recordIfAbsent(
        eventId: UUID,
        consumerGroup: String,
        eventType: String,
    ): Boolean {
        val id = CouponIssueEventHandledJpaId(eventId = eventId, consumerGroup = consumerGroup)
        if (couponIssueEventHandledJpaRepository.existsById(id)) {
            return false
        }

        return try {
            couponIssueEventHandledJpaRepository.saveAndFlush(
                CouponIssueEventHandledJpaEntity(
                    id = id,
                    eventType = eventType,
                ),
            )
            true
        } catch (_: DataIntegrityViolationException) {
            false
        }
    }

    override fun exists(eventId: UUID, consumerGroup: String): Boolean =
        couponIssueEventHandledJpaRepository.existsById(
            CouponIssueEventHandledJpaId(eventId = eventId, consumerGroup = consumerGroup),
        )
}
