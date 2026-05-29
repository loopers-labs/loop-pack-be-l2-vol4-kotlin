package com.loopers.application.like

import com.loopers.domain.like.LikeAction
import com.loopers.domain.like.LikeEvent
import com.loopers.domain.like.LikeEventRepository
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

    @DisplayName("좋아요 변경 이벤트를 받으면, 같은 내용으로 LikeEvent를 append한다.")
    @Test
    fun appendsLikeEvent_whenEventReceived() {
        handler.append(LikeChangedEvent(10L, 100L, LikeAction.LIKE))

        val captor = argumentCaptor<LikeEvent>()
        verify(likeEventRepository).append(captor.capture())
        assertAll(
            { assertThat(captor.firstValue.userId).isEqualTo(10L) },
            { assertThat(captor.firstValue.productId).isEqualTo(100L) },
            { assertThat(captor.firstValue.action).isEqualTo(LikeAction.LIKE) },
        )
    }

    @DisplayName("append가 실패해도 예외를 전파하지 않는다. (이력 적재 실패가 본 트랜잭션을 막지 않음)")
    @Test
    fun doesNotPropagate_whenAppendFails() {
        whenever(likeEventRepository.append(any())).doThrow(RuntimeException("DB down"))

        assertDoesNotThrow { handler.append(LikeChangedEvent(10L, 100L, LikeAction.UNLIKE)) }
    }
}
