package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponPublishInbox
import org.springframework.data.jpa.repository.JpaRepository

interface CouponPublishInboxJpaRepository : JpaRepository<CouponPublishInbox, Long> {
    fun existsByIdempotencyKeyAndDeletedAtIsNull(idempotencyKey: String): Boolean
}
