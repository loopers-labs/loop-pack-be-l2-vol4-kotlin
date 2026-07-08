package com.loopers.application.metrics

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.eventhandled.EventHandledModel
import com.loopers.domain.eventhandled.EventHandledRepository
import com.loopers.domain.metrics.ProductMetricsModel
import com.loopers.domain.metrics.ProductMetricsRepository
import com.loopers.interfaces.consumer.EventMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProductMetricsServiceTest {
    private val objectMapper = ObjectMapper()

    /** 항상 미처리(false)로 응답해 멱등 분기를 통과시키는 Fake. */
    private class FakeEventHandledRepository : EventHandledRepository {
        override fun existsByEventId(eventId: String): Boolean = false
        override fun save(model: EventHandledModel): EventHandledModel = model
    }

    /** upsert 로 넘어온 productId 를 기록만 하는 Fake. */
    private class RecordingMetricsRepository : ProductMetricsRepository {
        val upsertedProductIds = mutableListOf<Long>()

        override fun upsert(productId: Long, likeDelta: Long, viewDelta: Long, salesDelta: Long) {
            upsertedProductIds += productId
        }

        override fun findByProductId(productId: Long): ProductMetricsModel? = null
    }

    private val metricsRepository = RecordingMetricsRepository()
    private val service = ProductMetricsService(FakeEventHandledRepository(), metricsRepository)

    @DisplayName("OrderCreated 항목에 productId/quantity 가 없으면, NPE 없이 스킵하고 upsert 하지 않는다.")
    @Test
    fun skipsItem_whenOrderCreatedFieldsMissing() {
        // arrange: items[0] 에 productId/quantity 누락 (빈 객체)
        val payload = objectMapper.createObjectNode()
        payload.putArray("items").addObject()
        val message = EventMessage(
            eventId = "evt-missing",
            eventType = "OrderCreated",
            aggregateId = 0L,
            payload = payload,
        )

        // act
        service.handle(message)

        // assert
        assertThat(metricsRepository.upsertedProductIds).isEmpty()
    }

    @DisplayName("OrderCreated 항목이 정상이면, 해당 상품에 판매 수량이 upsert 된다.")
    @Test
    fun upsertsItem_whenOrderCreatedFieldsPresent() {
        // arrange
        val payload = objectMapper.createObjectNode()
        payload.putArray("items").addObject().apply {
            put("productId", 202L)
            put("quantity", 3L)
        }
        val message = EventMessage(
            eventId = "evt-ok",
            eventType = "OrderCreated",
            aggregateId = 0L,
            payload = payload,
        )

        // act
        service.handle(message)

        // assert
        assertThat(metricsRepository.upsertedProductIds).containsExactly(202L)
    }
}
