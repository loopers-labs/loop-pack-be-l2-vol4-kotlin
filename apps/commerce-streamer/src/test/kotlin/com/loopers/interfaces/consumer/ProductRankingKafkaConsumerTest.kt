package com.loopers.interfaces.consumer

import com.loopers.ranking.application.RankingAccumulateService
import com.loopers.ranking.domain.ScoreChange
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.record.TimestampType
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.kafka.support.Acknowledgment
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

class ProductRankingKafkaConsumerTest {
    private val rankingAccumulateService: RankingAccumulateService = mock()
    private val consumer = ProductRankingKafkaConsumer(rankingAccumulateService)
    private val acknowledgment: Acknowledgment = mock()

    private fun record(topic: String, json: String): ConsumerRecord<String, ByteArray> =
        ConsumerRecord(topic, 0, 0, "1", json.toByteArray())

    private fun recordAt(topic: String, json: String, timestamp: Long): ConsumerRecord<String, ByteArray> =
        ConsumerRecord(
            topic,
            0,
            0,
            timestamp,
            TimestampType.CREATE_TIME,
            ConsumerRecord.NULL_SIZE,
            ConsumerRecord.NULL_SIZE,
            "1",
            json.toByteArray(),
            RecordHeaders(),
            Optional.empty(),
        )

    @DisplayName("좋아요는 +0.2, 취소는 -0.2 의 점수 변화량으로 변환해 랭킹 적재를 위임한다.")
    @Test
    fun convertsLikeEventsToScoreChanges() {
        val liked = """{"eventId":"e-1","eventType":"ProductLikedEvent","productId":1}"""
        val unliked = """{"eventId":"e-2","eventType":"ProductUnlikedEvent","productId":1}"""

        consumer.consumeProductEvents(listOf(record("product-events", liked), record("product-events", unliked)), acknowledgment)

        assertAll(
            { verify(rankingAccumulateService).accumulate(eq("e-1"), any(), eq(listOf(ScoreChange(1, BigDecimal("0.2"))))) },
            { verify(rankingAccumulateService).accumulate(eq("e-2"), any(), eq(listOf(ScoreChange(1, BigDecimal("-0.2"))))) },
            { verify(acknowledgment).acknowledge() },
        )
    }

    @DisplayName("주문은 수량·단가와 무관하게 order line 당 +0.7 변화량 목록으로 변환한다.")
    @Test
    fun convertsOrderLinesToFixedScoreChanges() {
        val json = """{"eventId":"e-1","eventType":"OrderCreatedEvent","orderId":10,
            "items":[{"productId":1,"quantity":100,"unitPrice":10},{"productId":2,"quantity":1,"unitPrice":1000000}]}"""

        consumer.consumeOrderEvents(listOf(record("order-events", json)), acknowledgment)

        verify(rankingAccumulateService).accumulate(
            eq("e-1"),
            any(),
            eq(listOf(ScoreChange(1, BigDecimal("0.7")), ScoreChange(2, BigDecimal("0.7")))),
        )
    }

    @DisplayName("조회는 +0.1 변화량으로 변환하고, record timestamp 를 발생 시각으로 전달한다.")
    @Test
    fun convertsViewToScoreChangeWithRecordTimestamp() {
        val json = """{"eventId":"e-1","eventType":"ProductViewedEvent","productId":1}"""

        consumer.consumeUserActionEvents(listOf(recordAt("user-action-events", json, 1721000000000)), acknowledgment)

        verify(rankingAccumulateService).accumulate(
            eq("e-1"),
            eq(Instant.ofEpochMilli(1721000000000)),
            eq(listOf(ScoreChange(1, BigDecimal("0.1")))),
        )
    }

    @DisplayName("역직렬화 불가 레코드는 위임 없이 건너뛰고 ack 한다.")
    @Test
    fun skipsMalformedRecord() {
        consumer.consumeProductEvents(listOf(record("product-events", "not-json")), acknowledgment)

        assertAll(
            { verifyNoInteractions(rankingAccumulateService) },
            { verify(acknowledgment).acknowledge() },
        )
    }
}
