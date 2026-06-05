package com.loopers.domain.order.unit

import com.loopers.domain.order.application.service.OrderService
import com.loopers.domain.order.port.OrderRepository
import com.loopers.domain.order.support.OrderSteps.Companion.주문항목_도메인_생성
import com.loopers.domain.order.support.OrderSteps.Companion.주문_도메인_생성
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderServiceTest {
    @Test
    fun `주문을_저장한다`() {
        val orderRepository = mockk<OrderRepository>()
        val orderService = OrderService(orderRepository)
        every { orderRepository.save(any()) } returns 주문_도메인_생성(id = 100L)

        val order = orderService.placeOrder(
            orderedUserId = 1L,
            items = listOf(주문항목_도메인_생성()),
        )

        assertThat(order.id).isEqualTo(100L)
        assertThat(order.paymentPrice.value).isEqualTo(20_000)
        verify(exactly = 1) { orderRepository.save(any()) }
    }

    @Test
    fun `주문항목이_없으면_BAD_REQUEST가_발생한다`() {
        val orderRepository = mockk<OrderRepository>()
        val orderService = OrderService(orderRepository)

        val ex = assertThrows<CoreException> {
            orderService.placeOrder(
                orderedUserId = 1L,
                items = emptyList(),
            )
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        verify(exactly = 0) { orderRepository.save(any()) }
    }
}
