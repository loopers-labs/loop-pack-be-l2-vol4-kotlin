package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.metrics.application.ProductMetricsService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.support.Acknowledgment

class ProductMetricsKafkaConsumerTest {
    private val productMetricsService: ProductMetricsService = mock()
    private val objectMapper = ObjectMapper()
    private val consumer = ProductMetricsKafkaConsumer(productMetricsService, objectMapper)
    private val acknowledgment: Acknowledgment = mock()

    private fun record(json: String): ConsumerRecord<Any, Any> =
        ConsumerRecord("catalog-events", 0, 0, "1", json.toByteArray())

    @DisplayName("레코드를 파싱해 eventId/eventType/payload 로 서비스에 위임하고, 전부 처리한 뒤 ack 한다.")
    @Test
    fun delegatesParsedRecordsToServiceAndAcknowledges() {
        val json = """{"eventId":"e-1","eventType":"ProductLikedEvent","productId":1}"""

        consumer.consume(listOf(record(json)), acknowledgment)

        assertAll(
            { verify(productMetricsService).handle("e-1", "ProductLikedEvent", objectMapper.readTree(json)) },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("파싱 불가 레코드는 건너뛰고 나머지를 처리한다.")
    @Test
    fun skipsMalformedRecordAndProcessesRest() {
        val valid = """{"eventId":"e-2","eventType":"ProductViewedEvent","productId":1}"""

        consumer.consume(listOf(record("not-json"), record(valid)), acknowledgment)

        assertAll(
            { verify(productMetricsService).handle(eq("e-2"), eq("ProductViewedEvent"), any()) },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("eventId 가 없는 레코드는 서비스에 위임하지 않고 건너뛴다.")
    @Test
    fun skipsRecordWithoutEventId() {
        consumer.consume(listOf(record("""{"eventType":"ProductLikedEvent","productId":1}""")), acknowledgment)

        assertAll(
            { verify(productMetricsService, never()).handle(any(), any(), any()) },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("서비스 처리 중 예외가 나면 전파하고 ack 하지 않는다 — 배치 재전달로 이어진다.")
    @Test
    fun propagatesAndSkipsAck_whenServiceFails() {
        doThrow(RuntimeException("db down")).whenever(productMetricsService).handle(any(), any(), any())

        assertThrows<RuntimeException> {
            consumer.consume(listOf(record("""{"eventId":"e-1","eventType":"ProductLikedEvent","productId":1}""")), acknowledgment)
        }
        verify(acknowledgment, never()).acknowledge()
    }
}
