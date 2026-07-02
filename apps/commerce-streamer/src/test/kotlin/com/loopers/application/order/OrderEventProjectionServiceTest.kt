package com.loopers.application.order

import com.loopers.domain.event.EventHandled
import com.loopers.domain.event.EventHandledRepository
import com.loopers.domain.useraction.UserActionLog
import com.loopers.domain.useraction.UserActionLogRepository
import com.loopers.domain.useraction.UserActionType
import com.loopers.event.OrderEventMessage
import com.loopers.event.OrderEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.ZonedDateTime

class OrderEventProjectionServiceTest {
    @DisplayName("주문 이벤트를 유저 행동 로그로 반영한다")
    @Test
    fun projectsOrderEventToUserActionLog() {
        val fixture = Fixture()
        val message = createMessage(eventId = "event-1", eventType = OrderEventType.ORDER_CREATED)

        fixture.service.project(message)

        val userActionLog = fixture.userActionLogRepository.logs.single()
        assertAll(
            { assertThat(userActionLog.eventId).isEqualTo("event-1") },
            { assertThat(userActionLog.actionType).isEqualTo(UserActionType.ORDER_CREATED) },
            { assertThat(userActionLog.memberId).isEqualTo(1L) },
            { assertThat(userActionLog.aggregateId).isEqualTo(20L) },
            { assertThat(userActionLog.productId).isNull() },
            { assertThat(fixture.eventHandledRepository.exists("event-1")).isTrue() },
        )
    }

    @DisplayName("이미 처리한 주문 이벤트는 다시 반영하지 않는다")
    @Test
    fun skipsAlreadyHandledEvent() {
        val fixture = Fixture()
        val message = createMessage(eventId = "event-1", eventType = OrderEventType.PAYMENT_SUCCEEDED)

        fixture.service.project(message)
        fixture.service.project(message)

        assertThat(fixture.userActionLogRepository.logs).hasSize(1)
    }

    private class Fixture {
        val eventHandledRepository = FakeEventHandledRepository()
        val userActionLogRepository = FakeUserActionLogRepository()
        val service = OrderEventProjectionService(
            eventHandledRepository = eventHandledRepository,
            userActionLogRepository = userActionLogRepository,
        )
    }

    private class FakeEventHandledRepository : EventHandledRepository {
        private val events = mutableSetOf<String>()

        override fun exists(eventId: String): Boolean {
            return eventId in events
        }

        override fun save(eventHandled: EventHandled): EventHandled {
            events.add(eventHandled.eventId)
            return eventHandled
        }
    }

    private class FakeUserActionLogRepository : UserActionLogRepository {
        val logs = mutableListOf<UserActionLog>()

        override fun save(userActionLog: UserActionLog): UserActionLog {
            logs.add(userActionLog)
            return userActionLog
        }
    }

    private fun createMessage(
        eventId: String,
        eventType: OrderEventType,
    ): OrderEventMessage {
        return OrderEventMessage(
            eventId = eventId,
            eventType = eventType,
            aggregateId = 20L,
            orderId = 20L,
            orderNumber = "order-20",
            memberId = 1L,
            paymentId = 30L,
            amount = 10_000L,
            occurredAt = ZonedDateTime.parse("2026-07-02T10:00:00+09:00"),
        )
    }
}
