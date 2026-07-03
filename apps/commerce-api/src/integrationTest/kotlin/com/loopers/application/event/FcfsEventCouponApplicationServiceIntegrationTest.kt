package com.loopers.application.event

import com.loopers.domain.coupon.CouponPublishEventType
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.EventCoupon
import com.loopers.domain.event.Event
import com.loopers.infrastructure.coupon.CouponPublishOutboxJpaRepository
import com.loopers.infrastructure.coupon.EventCouponJpaRepository
import com.loopers.infrastructure.event.EventJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@SpringBootTest(properties = ["spring.kafka.listener.auto-startup=false"])
class FcfsEventCouponApplicationServiceIntegrationTest @Autowired constructor(
    private val service: FcfsEventCouponApplicationService,
    private val eventJpaRepository: EventJpaRepository,
    private val eventCouponJpaRepository: EventCouponJpaRepository,
    private val outboxJpaRepository: CouponPublishOutboxJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoBean
    lateinit var kafkaTemplate: KafkaTemplate<Any, Any>

    @BeforeEach
    fun setUpKafkaTemplate() {
        whenever(kafkaTemplate.send(any<String>(), any(), any()))
            .thenReturn(CompletableFuture.completedFuture(stubSendResult()))
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun getReturnsAlreadyRegisteredBeforeEventEnded() {
        val coupon = saveEventCoupon(totalQuantity = 1)
        service.request(userId = 1L, couponId = coupon.id, now = LocalDateTime.of(2026, 7, 3, 11, 0))

        val result = service.get(userId = 1L, couponId = coupon.id, now = LocalDateTime.of(2026, 7, 4, 11, 0))

        assertThat(result.status).isEqualTo(EventCouponStatus.ALREADY_REGISTERED)
    }

    @Test
    fun getReturnsEventEndedWhenQuantityIsExhausted() {
        val coupon = saveEventCoupon(totalQuantity = 1)
        service.request(userId = 1L, couponId = coupon.id, now = LocalDateTime.of(2026, 7, 3, 11, 0))

        val result = service.get(userId = 2L, couponId = coupon.id, now = LocalDateTime.of(2026, 7, 3, 11, 1))

        assertThat(result.status).isEqualTo(EventCouponStatus.EVENT_ENDED)
    }

    @Test
    fun requestStoresOutboxAndReservesQuantityWhenAvailable() {
        val coupon = saveEventCoupon(totalQuantity = 2)

        val result = service.request(userId = 1L, couponId = coupon.id, now = LocalDateTime.of(2026, 7, 3, 11, 0))

        val reloaded = eventCouponJpaRepository.findById(coupon.id).orElseThrow()
        val outbox = outboxJpaRepository.findAll().single()
        assertAll(
            { assertThat(result.status).isEqualTo(EventCouponStatus.REQUESTED) },
            { assertThat(result.idempotencyKey).isNotBlank() },
            { assertThat(reloaded.issuedQuantity).isEqualTo(1) },
            { assertThat(outbox.eventType).isEqualTo(CouponPublishEventType.COUPON_PUBLISH_REQUESTED) },
            { assertThat(outbox.couponId).isEqualTo(coupon.id) },
            { assertThat(outbox.userId).isEqualTo(1L) },
        )
    }

    @Test
    fun requestReturnsEventEndedWithoutOutboxWhenOutsideEventPeriod() {
        val coupon = saveEventCoupon(totalQuantity = 2)

        val result = service.request(userId = 1L, couponId = coupon.id, now = LocalDateTime.of(2026, 7, 3, 9, 59))

        val reloaded = eventCouponJpaRepository.findById(coupon.id).orElseThrow()
        assertAll(
            { assertThat(result.status).isEqualTo(EventCouponStatus.EVENT_ENDED) },
            { assertThat(result.idempotencyKey).isNull() },
            { assertThat(reloaded.issuedQuantity).isEqualTo(0) },
            { assertThat(outboxJpaRepository.count()).isEqualTo(0) },
        )
    }

    @Test
    fun requestReturnsAlreadyRegisteredWithoutAdditionalReservation() {
        val coupon = saveEventCoupon(totalQuantity = 2)
        service.request(userId = 1L, couponId = coupon.id, now = LocalDateTime.of(2026, 7, 3, 11, 0))

        val result = service.request(userId = 1L, couponId = coupon.id, now = LocalDateTime.of(2026, 7, 3, 11, 1))

        val reloaded = eventCouponJpaRepository.findById(coupon.id).orElseThrow()
        assertAll(
            { assertThat(result.status).isEqualTo(EventCouponStatus.ALREADY_REGISTERED) },
            { assertThat(reloaded.issuedQuantity).isEqualTo(1) },
            { assertThat(outboxJpaRepository.count()).isEqualTo(1) },
        )
    }

    private fun saveEventCoupon(totalQuantity: Long): EventCoupon {
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
                totalQuantity = totalQuantity,
            ),
        )
    }

    private fun stubSendResult(): SendResult<Any, Any> =
        SendResult(
            ProducerRecord("test-topic", "test-key", "test-value"),
            RecordMetadata(TopicPartition("test-topic", 0), 0, 0, 0, 0, 0),
        )
}
