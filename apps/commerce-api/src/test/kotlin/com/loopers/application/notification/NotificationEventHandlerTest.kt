package com.loopers.application.notification

import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.payment.PaymentSucceededEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class NotificationEventHandlerTest {
    private val handler = NotificationEventHandler()

    @DisplayName("주문 생성 이벤트를 주문 접수 알림 메시지로 변환한다.")
    @Test
    fun messageForOrderCreated() {
        val event = OrderCreatedEvent(orderId = 1L, userId = 2L, items = emptyList())
        assertThat(handler.message(event))
            .isEqualTo("NOTIFY user=2 주문(1)이 접수되었습니다.")
    }

    @DisplayName("결제 성공 이벤트를 주문 완료 알림 메시지로 변환한다.")
    @Test
    fun messageForPaymentSucceeded() {
        val event = PaymentSucceededEvent(orderId = 1L, userId = 2L, items = emptyList())
        assertThat(handler.message(event))
            .isEqualTo("NOTIFY user=2 주문(1) 결제가 완료되었습니다.")
    }

    @DisplayName("핸들러 호출은 예외 없이 완료된다(스모크).")
    @Test
    fun handleDoesNotThrow() {
        handler.handle(OrderCreatedEvent(orderId = 1L, userId = 2L, items = emptyList()))
        handler.handle(PaymentSucceededEvent(orderId = 1L, userId = 2L, items = emptyList()))
    }
}
