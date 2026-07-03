package com.loopers.application.event

import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.EventCoupon
import com.loopers.domain.coupon.EventCouponRepository
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
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest(properties = ["spring.kafka.listener.auto-startup=false"])
class FcfsEventCouponConcurrencyIntegrationTest @Autowired constructor(
    private val service: FcfsEventCouponApplicationService,
    private val eventJpaRepository: EventJpaRepository,
    private val eventCouponJpaRepository: EventCouponJpaRepository,
    private val outboxJpaRepository: CouponPublishOutboxJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockitoBean
    lateinit var kafkaTemplate: KafkaTemplate<Any, Any>

    @MockitoSpyBean
    lateinit var eventCouponRepository: EventCouponRepository

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
    fun concurrentRequestsCannotReserveMoreThanTotalQuantity() {
        val coupon = saveEventCoupon(totalQuantity = 3)
        val requested = AtomicInteger(0)

        runConcurrently(times = 12) { index ->
            val result = service.request(
                userId = index + 1L,
                couponId = coupon.id,
                now = LocalDateTime.of(2026, 7, 3, 11, 0),
            )
            if (result.status == EventCouponStatus.REQUESTED) {
                requested.incrementAndGet()
            }
        }

        val reloaded = eventCouponJpaRepository.findById(coupon.id).orElseThrow()
        assertAll(
            { assertThat(requested.get()).isEqualTo(3) },
            { assertThat(reloaded.issuedQuantity).isEqualTo(3) },
            { assertThat(outboxJpaRepository.count()).isEqualTo(3) },
        )
    }

    @Test
    fun concurrentRequestsTriggerCouponQuantityDecrementWithinTimeout() {
        val coupon = saveEventCoupon(totalQuantity = 3)
        val requestCount = 12

        runConcurrently(times = requestCount) { index ->
            service.request(
                userId = index + 1L,
                couponId = coupon.id,
                now = LocalDateTime.of(2026, 7, 3, 11, 0),
            )
        }

        verify(eventCouponRepository, timeout(3_000).times(requestCount))
            .reserveOneIfAvailable(coupon.id)
        val reloaded = eventCouponJpaRepository.findById(coupon.id).orElseThrow()
        assertAll(
            { assertThat(reloaded.issuedQuantity).isEqualTo(3) },
            { assertThat(reloaded.remainingQuantity).isEqualTo(0) },
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

    private fun runConcurrently(times: Int, block: (Long) -> Unit) {
        val executor = Executors.newFixedThreadPool(times)
        val ready = CountDownLatch(times)
        val start = CountDownLatch(1)
        val done = CountDownLatch(times)
        val failures = mutableListOf<Throwable>()

        repeat(times) { index ->
            executor.submit {
                try {
                    ready.countDown()
                    start.await(3, TimeUnit.SECONDS)
                    block(index.toLong())
                } catch (t: Throwable) {
                    synchronized(failures) { failures += t }
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await(3, TimeUnit.SECONDS)
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(failures).isEmpty()
    }

    private fun stubSendResult(): SendResult<Any, Any> =
        SendResult(
            ProducerRecord("test-topic", "test-key", "test-value"),
            RecordMetadata(TopicPartition("test-topic", 0), 0, 0, 0, 0, 0),
        )
}
