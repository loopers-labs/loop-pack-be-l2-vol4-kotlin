package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponPublishEventType
import com.loopers.domain.coupon.CouponPublishOutbox
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.ZonedDateTime

interface CouponPublishOutboxJpaRepository : JpaRepository<CouponPublishOutbox, Long> {
    fun existsByEventTypeAndCouponIdAndUserIdAndDeletedAtIsNull(
        eventType: CouponPublishEventType,
        couponId: Long,
        userId: Long,
    ): Boolean

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        update CouponPublishOutbox outbox
           set outbox.publishedAt = :publishedAt
         where outbox.id = :outboxId
           and outbox.deletedAt is null
        """,
    )
    fun markPublished(
        @Param("outboxId") outboxId: Long,
        @Param("publishedAt") publishedAt: ZonedDateTime,
    ): Int
}
