package com.loopers.application.event

import com.loopers.domain.coupon.CouponPublishEventType
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.EventCoupon
import com.loopers.domain.event.Event
import com.loopers.infrastructure.coupon.CouponPublishInboxJpaRepository
import com.loopers.infrastructure.coupon.EventCouponJpaRepository
import com.loopers.infrastructure.coupon.IssuedCouponJpaRepository
import com.loopers.infrastructure.event.EventJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest(properties = ["spring.kafka.listener.auto-startup=false"])
class CouponPublishConsumerServiceIntegrationTest @Autowired constructor(
    private val consumerService: CouponPublishConsumerService,
    private val eventJpaRepository: EventJpaRepository,
    private val eventCouponJpaRepository: EventCouponJpaRepository,
    private val inboxJpaRepository: CouponPublishInboxJpaRepository,
    private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun processDeduplicatesByIdempotencyKeyAndCreatesIssuedCouponOnce() {
        val coupon = saveEventCoupon()
        val message = CouponPublishRequestedMessage(
            idempotencyKey = "01986f7d-7b6a-7c80-a0f6-b7e1a9bcb2af",
            eventId = 3L,
            couponId = coupon.id,
            userId = 1L,
            eventType = CouponPublishEventType.COUPON_PUBLISH_REQUESTED,
        )

        consumerService.process(message)
        consumerService.process(message)

        assertAll(
            { assertThat(inboxJpaRepository.count()).isEqualTo(1) },
            { assertThat(issuedCouponJpaRepository.count()).isEqualTo(1) },
            { assertThat(issuedCouponJpaRepository.findByUserIdAndCouponIdAndDeletedAtIsNull(1L, coupon.id)).isNotNull() },
        )
    }

    private fun saveEventCoupon(): EventCoupon {
        val event = eventJpaRepository.save(
            Event(
                name = "Summer coupon event",
                startsAt = LocalDateTime.of(2026, 7, 3, 10, 0),
                endsAt = LocalDateTime.of(2026, 7, 3, 18, 0),
            ),
        )
        return eventCouponJpaRepository.save(
            EventCoupon(
                name = "선착순 쿠폰",
                type = CouponType.FIXED,
                value = 1000,
                minOrderAmount = null,
                expiredAt = LocalDateTime.of(2026, 12, 31, 23, 59),
                eventId = event.id,
                totalQuantity = 10,
            ),
        )
    }
}
