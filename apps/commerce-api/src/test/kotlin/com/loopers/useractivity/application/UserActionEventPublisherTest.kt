package com.loopers.useractivity.application

import com.loopers.outbox.domain.EventMessagePublisher
import com.loopers.product.domain.event.ProductViewedEvent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserActionEventPublisherTest {
    private val eventMessagePublisher: EventMessagePublisher = mock()
    private val userActionEventPublisher = UserActionEventPublisher(eventMessagePublisher)

    @DisplayName("상품 조회 이벤트를 user-action-events 토픽에 key=productId 로 발행한다.")
    @Test
    fun publishesViewedEventToUserActionTopic() {
        val event = ProductViewedEvent(productId = 7L)

        userActionEventPublisher.onProductViewed(event)

        verify(eventMessagePublisher).publish("user-action-events", "7", event)
    }

    @DisplayName("발행이 실패해도 예외를 전파하지 않는다 — 조회 이벤트는 유실 허용.")
    @Test
    fun doesNotPropagate_whenPublishFails() {
        doThrow(RuntimeException("broker down")).whenever(eventMessagePublisher).publish(any(), any(), any())

        assertDoesNotThrow { userActionEventPublisher.onProductViewed(ProductViewedEvent(productId = 7L)) }
    }
}
