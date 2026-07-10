package com.loopers.domain.order.unit

import com.loopers.domain.order.application.OrderFacade
import com.loopers.domain.order.application.OrderQueueGateFacade
import com.loopers.domain.order.application.OrderQueueGatePolicy
import com.loopers.domain.order.application.info.OrderInfo
import com.loopers.domain.order.support.OrderSteps.Companion.주문_생성_커맨드
import com.loopers.domain.waitingqueue.application.WaitingQueueFacade
import com.loopers.domain.waitingqueue.port.TokenValidationResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderQueueGateFacadeTest {
    @Test
    fun `토큰_marker가_만료됐어도_같은_사용자와_멱등키의_커밋_주문은_복구한다`() {
        val waitingQueueFacade = mockk<WaitingQueueFacade>()
        val orderFacade = mockk<OrderFacade>()
        val gatePolicy = mockk<OrderQueueGatePolicy>()
        val gate = OrderQueueGateFacade(waitingQueueFacade, orderFacade, gatePolicy)
        val command = 주문_생성_커맨드(idempotencyKey = IDEMPOTENCY_KEY)
        val committedOrder = orderInfo()
        every { gatePolicy.requiresAdmission(command) } returns true
        every {
            waitingQueueFacade.validateForOrder(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        } throws CoreException(ErrorType.UNAUTHORIZED)
        every { orderFacade.findByIdempotencyKeyOrNull(USER_ID, IDEMPOTENCY_KEY) } returns committedOrder

        val result = gate.placeOrder(command, QUEUE_TOKEN, IDEMPOTENCY_KEY)

        assertThat(result).isEqualTo(committedOrder)
        verify(exactly = 0) { orderFacade.placeOrder(any()) }
        verify(exactly = 0) {
            waitingQueueFacade.consumeAfterOrderCreated(any(), any(), any())
            waitingQueueFacade.releaseAfterOrderFailed(any(), any(), any())
        }
    }

    @Test
    fun `PROCESSING_재시도에서_커밋된_주문을_찾으면_토큰을_consume하고_기존_주문을_반환한다`() {
        val waitingQueueFacade = mockk<WaitingQueueFacade>()
        val orderFacade = mockk<OrderFacade>()
        val gatePolicy = mockk<OrderQueueGatePolicy>()
        val gate = OrderQueueGateFacade(waitingQueueFacade, orderFacade, gatePolicy)
        val command = 주문_생성_커맨드(idempotencyKey = IDEMPOTENCY_KEY)
        val committedOrder = orderInfo()
        every { gatePolicy.requiresAdmission(command) } returns true
        every {
            waitingQueueFacade.validateForOrder(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        } returns TokenValidationResult.processingBySameIdempotencyKey(IDEMPOTENCY_KEY)
        every { orderFacade.findByIdempotencyKeyOrNull(USER_ID, IDEMPOTENCY_KEY) } returns committedOrder
        every {
            waitingQueueFacade.consumeAfterOrderCreated(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        } just Runs

        val result = gate.placeOrder(command, QUEUE_TOKEN, IDEMPOTENCY_KEY)

        assertThat(result).isEqualTo(committedOrder)
        verify(exactly = 1) {
            waitingQueueFacade.consumeAfterOrderCreated(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        }
        verify(exactly = 0) { orderFacade.placeOrder(any()) }
    }

    @Test
    fun `주문이_커밋된_뒤_예외가_발생하면_토큰을_release하지_않고_consume한다`() {
        val waitingQueueFacade = mockk<WaitingQueueFacade>()
        val orderFacade = mockk<OrderFacade>()
        val gatePolicy = mockk<OrderQueueGatePolicy>()
        val gate = OrderQueueGateFacade(waitingQueueFacade, orderFacade, gatePolicy)
        val command = 주문_생성_커맨드(idempotencyKey = IDEMPOTENCY_KEY)
        val committedOrder = orderInfo()
        val afterCommitFailure = IllegalStateException("after commit listener failed")
        every { gatePolicy.requiresAdmission(command) } returns true
        every {
            waitingQueueFacade.validateForOrder(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        } returns TokenValidationResult.valid()
        every { orderFacade.placeOrder(command) } throws afterCommitFailure
        every { orderFacade.findByIdempotencyKeyOrNull(USER_ID, IDEMPOTENCY_KEY) } returns committedOrder
        every {
            waitingQueueFacade.consumeAfterOrderCreated(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        } just Runs
        every {
            waitingQueueFacade.releaseAfterOrderFailed(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        } just Runs

        val thrown = assertThrows<IllegalStateException> {
            gate.placeOrder(command, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        }

        assertThat(thrown).isSameAs(afterCommitFailure)
        verify(exactly = 1) { orderFacade.findByIdempotencyKeyOrNull(USER_ID, IDEMPOTENCY_KEY) }
        verify(exactly = 1) {
            waitingQueueFacade.consumeAfterOrderCreated(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        }
        verify(exactly = 0) {
            waitingQueueFacade.releaseAfterOrderFailed(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        }
    }

    @Test
    fun `주문_실패_후_토큰_release가_실패하면_원래_오류_대신_SERVICE_UNAVAILABLE을_반환한다`() {
        val waitingQueueFacade = mockk<WaitingQueueFacade>()
        val orderFacade = mockk<OrderFacade>()
        val gatePolicy = mockk<OrderQueueGatePolicy>()
        val gate = OrderQueueGateFacade(waitingQueueFacade, orderFacade, gatePolicy)
        val command = 주문_생성_커맨드(idempotencyKey = IDEMPOTENCY_KEY)
        val orderFailure = CoreException(ErrorType.CONFLICT)
        val transitionFailure = CoreException(ErrorType.SERVICE_UNAVAILABLE)
        every { gatePolicy.requiresAdmission(command) } returns true
        every {
            waitingQueueFacade.validateForOrder(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        } returns TokenValidationResult.valid()
        every { orderFacade.placeOrder(command) } throws orderFailure
        every { orderFacade.findByIdempotencyKeyOrNull(USER_ID, IDEMPOTENCY_KEY) } returns null
        every {
            waitingQueueFacade.releaseAfterOrderFailed(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        } throws transitionFailure

        val thrown = assertThrows<CoreException> {
            gate.placeOrder(command, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        }

        assertThat(thrown).isSameAs(transitionFailure)
        assertThat(thrown.errorType).isEqualTo(ErrorType.SERVICE_UNAVAILABLE)
        assertThat(thrown.suppressed).containsExactly(orderFailure)
    }

    @Test
    fun `주문_실패_후_커밋_여부를_조회할_수_없으면_PROCESSING을_유지하고_SERVICE_UNAVAILABLE을_반환한다`() {
        val waitingQueueFacade = mockk<WaitingQueueFacade>()
        val orderFacade = mockk<OrderFacade>()
        val gatePolicy = mockk<OrderQueueGatePolicy>()
        val gate = OrderQueueGateFacade(waitingQueueFacade, orderFacade, gatePolicy)
        val command = 주문_생성_커맨드(idempotencyKey = IDEMPOTENCY_KEY)
        val orderFailure = CoreException(ErrorType.CONFLICT)
        val recoveryFailure = IllegalStateException("order store unavailable")
        every { gatePolicy.requiresAdmission(command) } returns true
        every {
            waitingQueueFacade.validateForOrder(USER_ID, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        } returns TokenValidationResult.valid()
        every { orderFacade.placeOrder(command) } throws orderFailure
        every {
            orderFacade.findByIdempotencyKeyOrNull(USER_ID, IDEMPOTENCY_KEY)
        } throws recoveryFailure

        val thrown = assertThrows<CoreException> {
            gate.placeOrder(command, QUEUE_TOKEN, IDEMPOTENCY_KEY)
        }

        assertThat(thrown.errorType).isEqualTo(ErrorType.SERVICE_UNAVAILABLE)
        assertThat(thrown.cause).isSameAs(recoveryFailure)
        assertThat(thrown.suppressed).containsExactly(orderFailure)
        verify(exactly = 0) {
            waitingQueueFacade.releaseAfterOrderFailed(any(), any(), any())
            waitingQueueFacade.consumeAfterOrderCreated(any(), any(), any())
        }
    }

    private fun orderInfo(): OrderInfo = OrderInfo(
        id = 100L,
        orderedUserId = USER_ID,
        issuedCouponId = null,
        status = "PAYMENT_PENDING",
        totalPrice = 10_000L,
        discountPrice = 0L,
        paymentPrice = 10_000L,
        items = emptyList(),
    )

    companion object {
        private const val USER_ID = 1L
        private const val QUEUE_TOKEN = "q_token"
        private const val IDEMPOTENCY_KEY = "order-idempotency-key"
    }
}
