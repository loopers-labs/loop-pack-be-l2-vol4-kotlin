package com.loopers.outbox.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.BaseEntity
import com.loopers.outbox.domain.EventMessagePublisher
import com.loopers.outbox.domain.OutboxEvent
import com.loopers.outbox.domain.OutboxEventRepository
import com.loopers.outbox.domain.OutboxStatus
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class OutboxRelayTest {
    private val outboxEventRepository: OutboxEventRepository = mock()
    private val eventMessagePublisher: EventMessagePublisher = mock()
    private val objectMapper = ObjectMapper()
    private val outboxRelay = OutboxRelay(outboxEventRepository, eventMessagePublisher, objectMapper)

    private fun outboxEvent(id: Long, aggregateType: String, aggregateId: Long): OutboxEvent {
        val event = OutboxEvent(aggregateType, aggregateId, "${aggregateType}Event", """{"eventId":"e-$id"}""")
        BaseEntity::class.java.getDeclaredField("id").apply { isAccessible = true }.set(event, id)
        return event
    }

    private fun givenPending(vararg events: OutboxEvent) {
        whenever(outboxEventRepository.findByStatus(eq(OutboxStatus.INIT), any())).thenReturn(events.toList())
    }

    private fun callNotPermitted(): CallNotPermittedException {
        val circuitBreaker = CircuitBreaker.ofDefaults("test-kafka-relay")
        circuitBreaker.transitionToOpenState()
        return CallNotPermittedException.createCallNotPermittedException(circuitBreaker)
    }

    @DisplayName("INIT 이벤트를 id 순으로 발행하고, 성공분 전체를 SENT 마킹한다.")
    @Test
    fun publishesInIdOrderAndMarksAllSent() {
        givenPending(outboxEvent(1L, "ORDER", 10L), outboxEvent(2L, "PRODUCT", 20L))

        outboxRelay.relay()

        val order = inOrder(eventMessagePublisher, outboxEventRepository)
        order.verify(eventMessagePublisher).publish(eq("order-events"), eq("10"), any())
        order.verify(eventMessagePublisher).publish(eq("catalog-events"), eq("20"), any())
        order.verify(outboxEventRepository).markSent(listOf(1L, 2L))
    }

    @DisplayName("ORDER aggregate 는 order-events 토픽에 key=aggregateId, payload JsonNode 로 발행한다.")
    @Test
    fun publishesOrderEventToOrderEventsTopic() {
        givenPending(outboxEvent(1L, "ORDER", 10L))

        outboxRelay.relay()

        verify(eventMessagePublisher).publish("order-events", "10", objectMapper.readTree("""{"eventId":"e-1"}"""))
    }

    @DisplayName("PRODUCT aggregate 는 catalog-events 토픽에 key=aggregateId 로 발행한다.")
    @Test
    fun publishesProductEventToCatalogEventsTopic() {
        givenPending(outboxEvent(1L, "PRODUCT", 20L))

        outboxRelay.relay()

        verify(eventMessagePublisher).publish("catalog-events", "20", objectMapper.readTree("""{"eventId":"e-1"}"""))
    }

    @DisplayName("발행이 실패하면 그 지점에서 중단하고, 성공분까지만 SENT 마킹한다 — 실패분 이후는 발행하지 않는다.")
    @Test
    fun marksOnlySucceededAsSent_whenPublishFailsMidway() {
        givenPending(outboxEvent(1L, "ORDER", 10L), outboxEvent(2L, "ORDER", 20L), outboxEvent(3L, "ORDER", 30L))
        doThrow(RuntimeException("broker down")).whenever(eventMessagePublisher).publish(any(), eq("20"), any())

        outboxRelay.relay()

        assertAll(
            { verify(eventMessagePublisher).publish(any(), eq("10"), any()) },
            { verify(eventMessagePublisher, never()).publish(any(), eq("30"), any()) },
            { verify(outboxEventRepository).markSent(listOf(1L)) },
        )
    }

    @DisplayName("대기 이벤트가 없으면 발행도 마킹도 하지 않는다.")
    @Test
    fun doesNothing_whenNoPendingEvents() {
        givenPending()

        outboxRelay.relay()

        assertAll(
            { verifyNoInteractions(eventMessagePublisher) },
            { verify(outboxEventRepository, never()).markSent(any()) },
        )
    }

    @DisplayName("서킷이 OPEN 이면(CallNotPermittedException) 그 폴링을 조용히 중단하고, markSent 도 예외 전파도 하지 않는다.")
    @Test
    fun stopsQuietly_whenCircuitOpen() {
        givenPending(outboxEvent(1L, "ORDER", 10L))
        doThrow(callNotPermitted()).whenever(eventMessagePublisher).publish(any(), any(), any())

        assertDoesNotThrow { outboxRelay.relay() }

        verify(outboxEventRepository, never()).markSent(any())
    }

    @DisplayName("서킷이 OPEN 이면 첫 이벤트에서 중단하고, 이후 이벤트에는 발행을 시도하지 않는다.")
    @Test
    fun doesNotAttemptRest_whenCircuitOpen() {
        givenPending(outboxEvent(1L, "ORDER", 10L), outboxEvent(2L, "ORDER", 20L))
        doThrow(callNotPermitted()).whenever(eventMessagePublisher).publish(any(), eq("10"), any())

        outboxRelay.relay()

        verify(eventMessagePublisher, never()).publish(any(), eq("20"), any())
    }
}
