package com.loopers.config.kafka

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer

class KafkaRecordRecovererTest {
    private val publishingRecoverer = mockk<DeadLetterPublishingRecoverer>(relaxed = true)
    private val handler = mockk<KafkaDeadLetterHandler>(relaxed = true)
    private val record = ConsumerRecord<String, ByteArray>("coupon-requests", 0, 0L, "1", byteArrayOf())
    private val exception = IllegalStateException("retry exhausted")

    @Test
    fun `DLT_발행이_성공한_뒤에만_도메인_실패_복구를_실행한다`() {
        every { handler.supports(record) } returns true
        val recoverer = KafkaConfig().recordRecoverer(publishingRecoverer, listOf(handler))

        recoverer.accept(record, exception)

        verifyOrder {
            publishingRecoverer.accept(record, exception)
            handler.afterPublished(record, exception)
        }
    }

    @Test
    fun `DLT_발행이_실패하면_도메인_실패_복구를_실행하지_않는다`() {
        every { handler.supports(record) } returns true
        every { publishingRecoverer.accept(record, exception) } throws IllegalStateException("DLT unavailable")
        val recoverer = KafkaConfig().recordRecoverer(publishingRecoverer, listOf(handler))

        assertThrows<IllegalStateException> {
            recoverer.accept(record, exception)
        }

        verify(exactly = 0) { handler.afterPublished(any(), any()) }
    }

    @Test
    fun `DLT_발행_뒤_도메인_복구가_실패하면_예외를_전파한다`() {
        every { handler.supports(record) } returns true
        every { handler.afterPublished(record, exception) } throws IllegalStateException("database unavailable")
        val recoverer = KafkaConfig().recordRecoverer(publishingRecoverer, listOf(handler))

        assertThrows<IllegalStateException> {
            recoverer.accept(record, exception)
        }

        verify(exactly = 1) { publishingRecoverer.accept(record, exception) }
    }
}
