package com.loopers.interfaces.event.like

import com.loopers.application.event.ProductLikeExternalEventSendService
import com.loopers.domain.like.event.ProductLikeEvent
import com.loopers.event.CatalogEventMessage
import com.loopers.event.CatalogEventType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ProductLikeEventMessageListenerTest {
    private val sendService = mock<ProductLikeExternalEventSendService>()
    private val listener = ProductLikeEventMessageListener(sendService)

    @DisplayName("좋아요 이벤트를 외부 카탈로그 이벤트로 발행 요청한다")
    @Test
    fun sendsLikedEvent() {
        listener.handle(ProductLikeEvent.Like(memberId = 1L, productId = 10L, brandId = 100L))

        val captor = argumentCaptor<CatalogEventMessage>()
        verify(sendService).send(captor.capture())
        val message = captor.firstValue

        assertThat(message.eventType).isEqualTo(CatalogEventType.PRODUCT_LIKED)
        assertThat(message.productId).isEqualTo(10L)
        assertThat(message.brandId).isEqualTo(100L)
        assertThat(message.memberId).isEqualTo(1L)
    }

    @DisplayName("좋아요 취소 이벤트를 외부 카탈로그 이벤트로 발행 요청한다")
    @Test
    fun sendsUnlikedEvent() {
        listener.handle(ProductLikeEvent.Unlike(memberId = 1L, productId = 10L, brandId = 100L))

        val captor = argumentCaptor<CatalogEventMessage>()
        verify(sendService).send(captor.capture())
        val message = captor.firstValue

        assertThat(message.eventType).isEqualTo(CatalogEventType.PRODUCT_UNLIKED)
        assertThat(message.productId).isEqualTo(10L)
        assertThat(message.brandId).isEqualTo(100L)
        assertThat(message.memberId).isEqualTo(1L)
    }
}
