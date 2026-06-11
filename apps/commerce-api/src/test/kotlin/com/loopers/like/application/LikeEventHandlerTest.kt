package com.loopers.like.application

import com.loopers.product.domain.event.ProductLikedEvent
import com.loopers.product.domain.event.ProductUnlikedEvent
import com.loopers.like.domain.LikeAction
import com.loopers.like.domain.LikeEvent
import com.loopers.like.domain.LikeEventRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LikeEventHandlerTest {
    private val likeEventRepository: LikeEventRepository = mock()
    private val handler = LikeEventHandler(likeEventRepository)

    @DisplayName("ProductLikedEvent를 구독하면, LIKE 이력을 append한다.")
    @Test
    fun appendsLikeEvent_onLiked() {
        handler.onLiked(ProductLikedEvent(10L, 100L))

        val captor = argumentCaptor<LikeEvent>()
        verify(likeEventRepository).append(captor.capture())
        assertAll(
            { assertThat(captor.firstValue.userId).isEqualTo(10L) },
            { assertThat(captor.firstValue.productId).isEqualTo(100L) },
            { assertThat(captor.firstValue.action).isEqualTo(LikeAction.LIKE) },
        )
    }

    @DisplayName("ProductUnlikedEvent를 구독하면, UNLIKE 이력을 append한다.")
    @Test
    fun appendsLikeEvent_onUnliked() {
        handler.onUnliked(ProductUnlikedEvent(10L, 100L))

        val captor = argumentCaptor<LikeEvent>()
        verify(likeEventRepository).append(captor.capture())
        assertThat(captor.firstValue.action).isEqualTo(LikeAction.UNLIKE)
    }

    @DisplayName("append가 실패해도 예외를 전파하지 않는다. (이력 적재 실패가 본 트랜잭션을 막지 않음)")
    @Test
    fun doesNotPropagate_whenAppendFails() {
        whenever(likeEventRepository.append(any())).doThrow(RuntimeException("DB down"))

        assertDoesNotThrow { handler.onLiked(ProductLikedEvent(10L, 100L)) }
    }
}
