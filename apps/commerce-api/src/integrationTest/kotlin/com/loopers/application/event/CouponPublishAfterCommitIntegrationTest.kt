package com.loopers.application.event

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
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@SpringBootTest(properties = ["spring.kafka.listener.auto-startup=false"])
class CouponPublishAfterCommitIntegrationTest @Autowired constructor(
    private val service: FcfsEventCouponApplicationService,
    private val eventJpaRepository: EventJpaRepository,
    private val eventCouponJpaRepository: EventCouponJpaRepository,
    private val outboxJpaRepository: CouponPublishOutboxJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    @Value("\${event-coupon.kafka.topic-name}") private val topicName: String,
) {
    @MockitoBean
    lateinit var kafkaTemplate: KafkaTemplate<Any, Any>

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun requestPublishesKafkaMessageAfterCommitAndMarksOutboxPublished() {
        val coupon = saveEventCoupon()
        whenever(kafkaTemplate.send(eq(topicName), any(), any()))
            .thenReturn(
                CompletableFuture.completedFuture(
                    SendResult<Any, Any>(
                        ProducerRecord<Any, Any>(topicName, "stub-key", CouponPublishRequestedMessage("stub-key", 3L, coupon.id, 1L)),
                        RecordMetadata(TopicPartition(topicName, 0), 0, 0, 0, 0, 0),
                    ),
                ),
            )

        val result = service.request(userId = 1L, couponId = coupon.id, now = LocalDateTime.of(2026, 7, 3, 11, 0))

        Thread.sleep(500)
        val idempotencyKey = requireNotNull(result.idempotencyKey)
        val outbox = outboxJpaRepository.findAll().single()
        val messageCaptor = argumentCaptor<CouponPublishRequestedMessage>()
        verify(kafkaTemplate).send(eq(topicName), eq(idempotencyKey), messageCaptor.capture())
        assertThat(messageCaptor.firstValue.couponId).isEqualTo(coupon.id)
        assertThat(outbox.publishedAt).isNotNull()
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
