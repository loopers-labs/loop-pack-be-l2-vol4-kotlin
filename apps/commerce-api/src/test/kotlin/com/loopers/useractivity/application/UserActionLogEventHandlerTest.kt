package com.loopers.useractivity.application

import com.loopers.order.domain.event.OrderCreatedEvent
import com.loopers.product.domain.event.ProductLikedEvent
import com.loopers.product.domain.event.ProductUnlikedEvent
import com.loopers.product.domain.event.ProductViewedEvent
import com.loopers.useractivity.domain.UserActionLog
import com.loopers.useractivity.domain.UserActionLogRepository
import com.loopers.useractivity.domain.UserActionType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserActionLogEventHandlerTest {
    private val userActionLogRepository: UserActionLogRepository = mock()
    private val handler = UserActionLogEventHandler(userActionLogRepository)

    private fun captureAppended(): UserActionLog {
        val captor = argumentCaptor<UserActionLog>()
        verify(userActionLogRepository).append(captor.capture())
        return captor.firstValue
    }

    @DisplayName("OrderCreatedEvent 를 구독하면, ORDER 행동 로그를 적재한다.")
    @Test
    fun appendsOrderLog_onOrderCreated() {
        handler.onOrderCreated(
            OrderCreatedEvent(orderId = 7L, orderKey = "key", userId = 1L, totalAmount = 10_000, items = emptyList()),
        )

        val log = captureAppended()
        assertAll(
            { assertThat(log.userId).isEqualTo(1L) },
            { assertThat(log.actionType).isEqualTo(UserActionType.ORDER) },
            { assertThat(log.targetType).isEqualTo("ORDER") },
            { assertThat(log.targetId).isEqualTo(7L) },
        )
    }

    @DisplayName("ProductViewedEvent 를 구독하면, VIEW 행동 로그를 적재한다. (userId 는 없을 수 있다)")
    @Test
    fun appendsViewLog_onProductViewed() {
        handler.onProductViewed(ProductViewedEvent(productId = 100L))

        val log = captureAppended()
        assertAll(
            { assertThat(log.userId).isNull() },
            { assertThat(log.actionType).isEqualTo(UserActionType.VIEW) },
            { assertThat(log.targetType).isEqualTo("PRODUCT") },
            { assertThat(log.targetId).isEqualTo(100L) },
        )
    }

    @DisplayName("ProductLikedEvent/ProductUnlikedEvent 를 구독하면, LIKE/UNLIKE 행동 로그를 적재한다.")
    @Test
    fun appendsLikeAndUnlikeLog() {
        handler.onLiked(ProductLikedEvent(10L, 100L))
        handler.onUnliked(ProductUnlikedEvent(10L, 100L))

        val captor = argumentCaptor<UserActionLog>()
        verify(userActionLogRepository, times(2)).append(captor.capture())
        assertAll(
            { assertThat(captor.firstValue.actionType).isEqualTo(UserActionType.LIKE) },
            { assertThat(captor.secondValue.actionType).isEqualTo(UserActionType.UNLIKE) },
            { assertThat(captor.allValues).allMatch { it.userId == 10L && it.targetType == "PRODUCT" && it.targetId == 100L } },
        )
    }

    @DisplayName("행동 로그 적재가 실패해도 예외를 전파하지 않는다. (유실 허용)")
    @Test
    fun doesNotPropagate_whenAppendFails() {
        whenever(userActionLogRepository.append(any())).doThrow(RuntimeException("DB down"))

        assertDoesNotThrow { handler.onProductViewed(ProductViewedEvent(productId = 100L)) }
    }
}
