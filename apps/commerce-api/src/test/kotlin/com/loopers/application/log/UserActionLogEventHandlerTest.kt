package com.loopers.application.log

import com.loopers.domain.like.LikeCreatedEvent
import com.loopers.domain.order.OrderCreatedEvent
import com.loopers.domain.payment.PaymentSucceededEvent
import com.loopers.domain.product.ProductViewedEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class UserActionLogEventHandlerTest {
    private val handler = UserActionLogEventHandler()

    @DisplayName("상품 조회 이벤트를 구조화 로그 문자열로 변환한다.")
    @Test
    fun describesProductViewed() {
        assertThat(handler.describe(ProductViewedEvent(productId = 10L)))
            .isEqualTo("USER_ACTION type=VIEW productId=10")
    }

    @DisplayName("좋아요 생성 이벤트를 구조화 로그 문자열로 변환한다.")
    @Test
    fun describesLikeCreated() {
        assertThat(handler.describe(LikeCreatedEvent(productId = 10L)))
            .isEqualTo("USER_ACTION type=LIKE productId=10")
    }

    @DisplayName("주문 생성 이벤트를 구조화 로그 문자열로 변환한다.")
    @Test
    fun describesOrderCreated() {
        val event = OrderCreatedEvent(
            orderId = 1L,
            userId = 2L,
            items = listOf(OrderCreatedEvent.Item(productId = 10L, quantity = 3)),
        )
        assertThat(handler.describe(event))
            .isEqualTo("USER_ACTION type=ORDER userId=2 orderId=1 items=1")
    }

    @DisplayName("결제 성공 이벤트를 구조화 로그 문자열로 변환한다.")
    @Test
    fun describesPaymentSucceeded() {
        val event = PaymentSucceededEvent(
            orderId = 1L,
            userId = 2L,
            items = listOf(PaymentSucceededEvent.Item(productId = 10L, quantity = 3)),
        )
        assertThat(handler.describe(event))
            .isEqualTo("USER_ACTION type=PAYMENT userId=2 orderId=1 items=1")
    }

    @DisplayName("핸들러 호출은 예외 없이 완료된다(스모크).")
    @Test
    fun handleDoesNotThrow() {
        handler.handle(ProductViewedEvent(productId = 10L))
        handler.handle(LikeCreatedEvent(productId = 10L))
    }
}
