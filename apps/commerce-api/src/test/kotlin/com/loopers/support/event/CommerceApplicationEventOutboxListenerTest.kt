package com.loopers.support.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.order.application.service.OrderService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.outbox.OutboxRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CommerceApplicationEventOutboxListenerTest {
    private val outboxRepository = mockk<OutboxRepository>(relaxed = true)
    private val orderService = mockk<OrderService>()
    private val listener = CommerceApplicationEventOutboxListener(
        outboxRepository = outboxRepository,
        orderService = orderService,
        objectMapper = ObjectMapper(),
    )

    @Test
    fun `결제_승인_이벤트의_주문_조회_실패가_NOT_FOUND가_아니면_예외를_전파한다`() {
        every { orderService.getById(10L) } throws IllegalStateException("db unavailable")

        assertThatThrownBy {
            listener.onPaymentApproved(PaymentApprovedApplicationEvent(paymentId = 1L, orderId = 10L))
        }.isInstanceOf(IllegalStateException::class.java)

        verify(exactly = 0) { outboxRepository.save(any()) }
    }

    @Test
    fun `결제_승인_이벤트의_주문이_없으면_outbox를_남기지_않는다`() {
        every { orderService.getById(10L) } throws CoreException(ErrorType.NOT_FOUND)

        listener.onPaymentApproved(PaymentApprovedApplicationEvent(paymentId = 1L, orderId = 10L))

        verify(exactly = 0) { outboxRepository.save(any()) }
    }
}
