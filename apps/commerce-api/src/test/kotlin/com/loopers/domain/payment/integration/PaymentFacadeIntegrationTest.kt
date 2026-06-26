package com.loopers.domain.payment.integration

import com.loopers.domain.order.support.OrderSteps.Companion.주문_도메인_생성
import com.loopers.domain.payment.application.PaymentFacade
import com.loopers.domain.payment.infrastructure.persistence.OutboxEventJpaRepository
import com.loopers.domain.payment.model.PaymentStatus
import com.loopers.domain.payment.port.PaymentCompensationPort
import com.loopers.domain.payment.port.PaymentGatewayPort
import com.loopers.domain.payment.port.PaymentGatewayResult
import com.loopers.domain.payment.port.PaymentGatewayStatus
import com.loopers.domain.payment.port.PaymentOrderPort
import com.loopers.domain.payment.port.PaymentRepository
import com.loopers.domain.payment.support.PaymentSteps.Companion.결제_요청_커맨드
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class PaymentFacadeIntegrationTest
    @Autowired
    constructor(
        private val paymentFacade: PaymentFacade,
        private val paymentRepository: PaymentRepository,
        private val outboxEventJpaRepository: OutboxEventJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @MockkBean
        private lateinit var paymentGatewayPort: PaymentGatewayPort

        @MockkBean
        private lateinit var paymentOrderPort: PaymentOrderPort

        @MockkBean
        private lateinit var paymentCompensationPort: PaymentCompensationPort

        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `동기_요청_실패_보상_중_예외가_나면_결제실패_전이와_outbox가_함께_롤백된다`() {
            val order = 주문_도메인_생성(id = 10L, orderedUserId = 1L)
            every { paymentOrderPort.getPayableOrder(1L, 10L) } returns order
            every { paymentGatewayPort.request(any()) } returns PaymentGatewayResult(
                transactionKey = "20260625:TR:test01",
                status = PaymentGatewayStatus.FAILED,
                reason = "한도초과",
            )
            every { paymentOrderPort.getPendingOrder(10L) } returns order
            justRun { paymentOrderPort.markPaymentFailed(10L) }
            every { paymentCompensationPort.restore(order) } throws IllegalStateException("재고 복원 실패")

            assertThrows<RuntimeException> {
                paymentFacade.request(결제_요청_커맨드(userId = 1L, orderId = 10L))
            }

            val payment = paymentRepository.findByOrderIdOrNull(10L)
            assertThat(payment?.status).isEqualTo(PaymentStatus.REQUESTED)
            assertThat(outboxEventJpaRepository.count()).isZero()
        }
    }
