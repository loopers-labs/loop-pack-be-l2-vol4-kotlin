package com.loopers.interfaces.consumer

import com.loopers.failure.application.ConsumedEventFailureRecorder
import com.loopers.failure.infrastructure.ConsumedEventFailure
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import java.nio.ByteBuffer

class DltKafkaConsumerTest {
    private val consumedEventFailureRecorder: ConsumedEventFailureRecorder = mock()
    private val consumer = DltKafkaConsumer(consumedEventFailureRecorder)
    private val acknowledgment: Acknowledgment = mock()

    private fun dltRecord(): ConsumerRecord<String, ByteArray> {
        val record = ConsumerRecord("product-events-dlt", 0, 5, "1", """{"eventId":"e-1"}""".toByteArray())
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_TOPIC, "product-events".toByteArray())
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_PARTITION, ByteBuffer.allocate(Int.SIZE_BYTES).putInt(2).array())
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_OFFSET, ByteBuffer.allocate(Long.SIZE_BYTES).putLong(42L).array())
        record.headers().add(KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP, "commerce-streamer-metrics-product".toByteArray())
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_FQCN, "java.lang.IllegalStateException".toByteArray())
        record.headers().add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "boom".toByteArray())
        return record
    }

    @DisplayName("DLT 레코드의 원본 위치·예외 헤더를 파싱해 실패 이력으로 적재하고 ack 한다.")
    @Test
    fun parsesDltHeadersIntoFailureRecord() {
        consumer.consumeDlt(listOf(dltRecord()), acknowledgment)

        val captor = argumentCaptor<ConsumedEventFailure>()
        verify(consumedEventFailureRecorder).record(captor.capture())
        val failure = captor.firstValue
        assertAll(
            { assertThat(failure.originalTopic).isEqualTo("product-events") },
            { assertThat(failure.originalPartition).isEqualTo(2) },
            { assertThat(failure.originalOffset).isEqualTo(42L) },
            { assertThat(failure.consumerGroup).isEqualTo("commerce-streamer-metrics-product") },
            { assertThat(failure.exceptionFqcn).isEqualTo("java.lang.IllegalStateException") },
            { assertThat(failure.exceptionMessage).isEqualTo("boom") },
            { assertThat(failure.payload).isEqualTo("""{"eventId":"e-1"}""") },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("헤더 없는 DLT 레코드는 토픽명에서 원본 토픽을 유추해 적재한다.")
    @Test
    fun derivesOriginalTopicFromDltTopicName_whenHeadersMissing() {
        val bare = ConsumerRecord("order-events-dlt", 0, 0, "1", "{}".toByteArray())

        consumer.consumeDlt(listOf(bare), acknowledgment)

        val captor = argumentCaptor<ConsumedEventFailure>()
        verify(consumedEventFailureRecorder).record(captor.capture())
        assertThat(captor.firstValue.originalTopic).isEqualTo("order-events")
    }

    @DisplayName("실패 이력 적재가 실패해도 예외를 전파하지 않고 배치를 ack 한다 — DLT 의 DLT 는 없다.")
    @Test
    fun acksEvenWhenRecordingFails() {
        doThrow(RuntimeException("db down")).whenever(consumedEventFailureRecorder).record(org.mockito.kotlin.any())

        consumer.consumeDlt(listOf(dltRecord()), acknowledgment)

        verify(acknowledgment).acknowledge()
    }
}
