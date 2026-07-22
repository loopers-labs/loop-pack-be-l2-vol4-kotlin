package com.loopers.application.metric

import com.loopers.infrastructure.metric.EventHandledEntity
import com.loopers.infrastructure.metric.EventHandledJpaRepository
import com.loopers.infrastructure.metric.ProductMetricJpaRepository
import com.loopers.interfaces.consumer.ProductMetricPayload
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class ProductMetricProcessorTest {

    private lateinit var productMetricJpaRepository: ProductMetricJpaRepository
    private lateinit var eventHandledJpaRepository: EventHandledJpaRepository
    private lateinit var productMetricProcessor: ProductMetricProcessor

    private fun payload(
        eventId: String = "event-1",
        productId: Long = 1L,
        type: String = "LIKE",
        delta: Long = 1L,
        occurredAt: ZonedDateTime = ZonedDateTime.parse("2026-06-07T10:00:00+09:00[Asia/Seoul]"),
    ) = ProductMetricPayload(
        eventId = eventId,
        productId = productId,
        type = type,
        delta = delta,
        occurredAt = occurredAt,
    )

    @BeforeEach
    fun setUp() {
        productMetricJpaRepository = mockk()
        eventHandledJpaRepository = mockk()
        productMetricProcessor = ProductMetricProcessor(productMetricJpaRepository, eventHandledJpaRepository)
    }

    @DisplayName("이미 처리된 eventId면, upsert와 이벤트 처리 기록 없이 종료한다.")
    @Test
    fun skipsProcessing_whenEventAlreadyHandled() {
        every { eventHandledJpaRepository.existsById("event-1") } returns true

        productMetricProcessor.process(payload())

        verify(exactly = 0) { productMetricJpaRepository.upsert(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { eventHandledJpaRepository.save(any()) }
    }

    @DisplayName("처리되지 않은 이벤트면, 발생일(KST) 기준 metric_date로 메트릭을 upsert하고 이벤트 처리 기록을 저장한다.")
    @Test
    fun upsertsMetricAndMarksHandled_whenEventNotHandled() {
        val savedHandled = slot<EventHandledEntity>()
        every { eventHandledJpaRepository.existsById("event-1") } returns false
        every {
            productMetricJpaRepository.upsert(
                productId = 1L,
                type = "LIKE",
                metricDate = LocalDate.of(2026, 6, 7),
                delta = 1L,
                occurredAt = any(),
            )
        } returns Unit
        every { eventHandledJpaRepository.save(capture(savedHandled)) } answers { savedHandled.captured }

        productMetricProcessor.process(payload())

        verify(exactly = 1) {
            productMetricJpaRepository.upsert(
                productId = 1L,
                type = "LIKE",
                metricDate = LocalDate.of(2026, 6, 7),
                delta = 1L,
                occurredAt = any(),
            )
        }
        assertThat(savedHandled.captured.eventId).isEqualTo("event-1")
    }

    @DisplayName("타 시간대 occurredAt이 와도, metric_date는 KST 날짜로 귀속된다 (UTC 23:30 → KST 다음 날).")
    @Test
    fun assignsMetricDateInKst_whenOccurredAtIsOtherZone() {
        val metricDate = slot<LocalDate>()
        every { eventHandledJpaRepository.existsById("event-1") } returns false
        every {
            productMetricJpaRepository.upsert(any(), any(), metricDate = capture(metricDate), any(), any())
        } returns Unit
        every { eventHandledJpaRepository.save(any()) } answers { firstArg() }

        productMetricProcessor.process(payload(occurredAt = ZonedDateTime.parse("2026-06-07T23:30:00Z")))

        assertThat(metricDate.captured).isEqualTo(LocalDate.of(2026, 6, 8))
    }
}
