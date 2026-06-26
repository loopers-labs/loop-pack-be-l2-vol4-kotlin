package com.loopers.domain.payment.unit

import com.loopers.domain.order.support.OrderSteps.Companion.주문_도메인_생성
import com.loopers.domain.payment.application.PaymentFacade
import com.loopers.domain.payment.application.PaymentResultHandler
import com.loopers.domain.payment.application.service.PaymentService
import com.loopers.domain.payment.model.OutboxEventStatus
import com.loopers.domain.payment.model.OutboxEventType
import com.loopers.domain.payment.port.PaymentCompensationPort
import com.loopers.domain.payment.port.PaymentGatewayPort
import com.loopers.domain.payment.port.PaymentGatewayRequest
import com.loopers.domain.payment.port.PaymentGatewayResult
import com.loopers.domain.payment.port.PaymentGatewayStatus
import com.loopers.domain.payment.port.PaymentGatewayUnknownException
import com.loopers.domain.payment.port.PaymentOrderPort
import com.loopers.domain.payment.support.FakeOutboxRepository
import com.loopers.domain.payment.support.FakePaymentRepository
import com.loopers.domain.payment.support.PaymentSteps.Companion.결제_콜백_커맨드
import com.loopers.domain.payment.support.PaymentSteps.Companion.결제_요청_커맨드
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentFacadeTest {
    @Test
    fun `PG_요청이_성공하면_거래키를_저장한다`() {
        val fixture = fixture()
        val order = 주문_도메인_생성(id = 10L, orderedUserId = 1L)
        val requestSlot = slot<PaymentGatewayRequest>()
        every { fixture.orderPort.getPayableOrder(1L, 10L) } returns order
        every { fixture.gatewayPort.request(capture(requestSlot)) } returns PaymentGatewayResult(
            transactionKey = "20260625:TR:test01",
            status = PaymentGatewayStatus.PENDING,
            reason = null,
        )

        val info = fixture.facade.request(결제_요청_커맨드(userId = 1L, orderId = 10L))

        assertThat(info.orderId).isEqualTo(10L)
        assertThat(info.transactionKey).isEqualTo("20260625:TR:test01")
        assertThat(info.status).isEqualTo("REQUESTED")
        assertThat(requestSlot.captured.amount).isEqualTo(order.paymentPrice.value)
    }

    @Test
    fun `PG_요청에_설정된_콜백_URL을_전달한다`() {
        val fixture = fixture()
        val order = 주문_도메인_생성(id = 10L, orderedUserId = 1L)
        val requestSlot = slot<PaymentGatewayRequest>()
        every { fixture.orderPort.getPayableOrder(1L, 10L) } returns order
        every { fixture.gatewayPort.request(capture(requestSlot)) } returns PaymentGatewayResult(
            transactionKey = "20260625:TR:test01",
            status = PaymentGatewayStatus.PENDING,
            reason = null,
        )

        fixture.facade.request(결제_요청_커맨드(userId = 1L, orderId = 10L))

        assertThat(requestSlot.captured.callbackUrl).isEqualTo(테스트_콜백_URL)
    }

    @Test
    fun `PG_상태를_확정할_수_없으면_UNKNOWN과_상태재조회_outbox를_남긴다`() {
        val fixture = fixture()
        val order = 주문_도메인_생성(id = 10L, orderedUserId = 1L)
        every { fixture.orderPort.getPayableOrder(1L, 10L) } returns order
        every { fixture.gatewayPort.request(any()) } throws PaymentGatewayUnknownException("timeout")

        val ex = assertThrows<CoreException> {
            fixture.facade.request(결제_요청_커맨드(userId = 1L, orderId = 10L))
        }

        val payment = fixture.paymentRepository.findByOrderIdOrNull(10L)
        assertThat(ex.errorType).isEqualTo(ErrorType.BAD_GATEWAY)
        assertThat(payment?.status?.name).isEqualTo("UNKNOWN")
        assertThat(fixture.outboxRepository.events.first().type).isEqualTo(OutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED)
    }

    @Test
    fun `승인_콜백은_결제를_승인하고_주문을_확정한다`() {
        val fixture = fixture()
        every { fixture.orderPort.getPayableOrder(1L, 10L) } returns 주문_도메인_생성(id = 10L, orderedUserId = 1L)
        every { fixture.gatewayPort.request(any()) } returns PaymentGatewayResult(
            transactionKey = "20260625:TR:test01",
            status = PaymentGatewayStatus.PENDING,
            reason = null,
        )
        justRun { fixture.orderPort.markOrdered(10L) }
        fixture.facade.request(결제_요청_커맨드(userId = 1L, orderId = 10L))

        val info = fixture.facade.handleCallback(결제_콜백_커맨드(status = "SUCCESS"))

        assertThat(info.status).isEqualTo("APPROVED")
        verify(exactly = 1) { fixture.orderPort.markOrdered(10L) }
    }

    @Test
    fun `중복_승인_콜백은_주문_확정을_반복하지_않는다`() {
        val fixture = fixture()
        every { fixture.orderPort.getPayableOrder(1L, 10L) } returns 주문_도메인_생성(id = 10L, orderedUserId = 1L)
        every { fixture.gatewayPort.request(any()) } returns PaymentGatewayResult(
            transactionKey = "20260625:TR:test01",
            status = PaymentGatewayStatus.PENDING,
            reason = null,
        )
        justRun { fixture.orderPort.markOrdered(10L) }
        fixture.facade.request(결제_요청_커맨드(userId = 1L, orderId = 10L))
        fixture.facade.handleCallback(결제_콜백_커맨드(status = "SUCCESS"))

        fixture.facade.handleCallback(결제_콜백_커맨드(status = "SUCCESS"))

        verify(exactly = 1) { fixture.orderPort.markOrdered(10L) }
    }

    @Test
    fun `실패_콜백은_주문을_실패시키고_보상을_수행한다`() {
        val fixture = fixture()
        val order = 주문_도메인_생성(id = 10L, orderedUserId = 1L)
        every { fixture.orderPort.getPayableOrder(1L, 10L) } returns order
        every { fixture.gatewayPort.request(any()) } returns PaymentGatewayResult(
            transactionKey = "20260625:TR:test01",
            status = PaymentGatewayStatus.PENDING,
            reason = null,
        )
        every { fixture.orderPort.getPendingOrder(10L) } returns order
        justRun { fixture.orderPort.markPaymentFailed(10L) }
        justRun { fixture.orderPort.detachCoupon(10L) }
        justRun { fixture.compensationPort.restore(order) }
        fixture.facade.request(결제_요청_커맨드(userId = 1L, orderId = 10L))

        val info = fixture.facade.handleCallback(결제_콜백_커맨드(status = "FAILED", reason = "한도초과"))

        assertThat(info.status).isEqualTo("FAILED")
        verify(exactly = 1) { fixture.orderPort.markPaymentFailed(10L) }
        verify(exactly = 1) { fixture.compensationPort.restore(order) }
        verify(exactly = 1) { fixture.orderPort.detachCoupon(10L) }
    }

    @Test
    fun `복구는_UNKNOWN_결제를_주문기준으로_조회해_승인_확정하고_이벤트를_처리완료한다`() {
        val fixture = fixture()
        val order = 주문_도메인_생성(id = 10L, orderedUserId = 1L)
        every { fixture.orderPort.getPayableOrder(1L, 10L) } returns order
        every { fixture.gatewayPort.request(any()) } throws PaymentGatewayUnknownException("timeout")
        assertThrows<CoreException> { fixture.facade.request(결제_요청_커맨드(userId = 1L, orderId = 10L)) }

        every { fixture.orderPort.getPendingOrder(10L) } returns order
        every { fixture.gatewayPort.findByOrderId(1L, 10L) } returns listOf(
            PaymentGatewayResult(
                transactionKey = "20260625:TR:test01",
                status = PaymentGatewayStatus.SUCCESS,
                reason = null,
            ),
        )
        justRun { fixture.orderPort.markOrdered(10L) }

        val result = fixture.facade.recoverUnknownPayments()

        val payment = fixture.paymentRepository.findByOrderIdOrNull(10L)
        assertThat(payment?.status?.name).isEqualTo("APPROVED")
        assertThat(result.recovered).isEqualTo(1)
        verify(exactly = 1) { fixture.orderPort.markOrdered(10L) }
        val syncEvent = fixture.outboxRepository.events
            .first { it.type == OutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED }
        assertThat(syncEvent.status).isEqualTo(OutboxEventStatus.PROCESSED)
    }

    @Test
    fun `복구시_PG가_여전히_미확정이면_결제를_그대로_두고_이벤트도_유지한다`() {
        val fixture = fixture()
        val order = 주문_도메인_생성(id = 10L, orderedUserId = 1L)
        every { fixture.orderPort.getPayableOrder(1L, 10L) } returns order
        every { fixture.gatewayPort.request(any()) } throws PaymentGatewayUnknownException("timeout")
        assertThrows<CoreException> { fixture.facade.request(결제_요청_커맨드(userId = 1L, orderId = 10L)) }

        every { fixture.orderPort.getPendingOrder(10L) } returns order
        every { fixture.gatewayPort.findByOrderId(1L, 10L) } returns emptyList()

        val result = fixture.facade.recoverUnknownPayments()

        val payment = fixture.paymentRepository.findByOrderIdOrNull(10L)
        assertThat(payment?.status?.name).isEqualTo("UNKNOWN")
        assertThat(result.recovered).isZero()
        val syncEvent = fixture.outboxRepository.events
            .first { it.type == OutboxEventType.PAYMENT_STATUS_SYNC_REQUESTED }
        assertThat(syncEvent.status).isEqualTo(OutboxEventStatus.PENDING)
    }

    private fun fixture(): Fixture {
        val paymentRepository = FakePaymentRepository()
        val outboxRepository = FakeOutboxRepository()
        val paymentService = PaymentService(paymentRepository, outboxRepository)
        val gatewayPort = mockk<PaymentGatewayPort>()
        val orderPort = mockk<PaymentOrderPort>()
        val compensationPort = mockk<PaymentCompensationPort>()
        return Fixture(
            paymentRepository = paymentRepository,
            outboxRepository = outboxRepository,
            gatewayPort = gatewayPort,
            orderPort = orderPort,
            compensationPort = compensationPort,
            facade = PaymentFacade(
                paymentService = paymentService,
                paymentGatewayPort = gatewayPort,
                paymentOrderPort = orderPort,
                paymentResultHandler = PaymentResultHandler(paymentService, orderPort, compensationPort),
                callbackUrl = 테스트_콜백_URL,
            ),
        )
    }

    private data class Fixture(
        val paymentRepository: FakePaymentRepository,
        val outboxRepository: FakeOutboxRepository,
        val gatewayPort: PaymentGatewayPort,
        val orderPort: PaymentOrderPort,
        val compensationPort: PaymentCompensationPort,
        val facade: PaymentFacade,
    )

    private companion object {
        const val 테스트_콜백_URL = "http://callback.test/api/v1/payments/callback"
    }
}
