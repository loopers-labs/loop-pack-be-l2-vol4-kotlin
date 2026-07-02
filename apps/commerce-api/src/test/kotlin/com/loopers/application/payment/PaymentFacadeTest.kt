package com.loopers.application.payment

import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgTransactionResult
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.payment.PgUnavailableException
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PaymentFacadeTest {
    private val userService = mockk<UserService>()
    private val orderService = mockk<OrderService>(relaxUnitFun = true)
    private val paymentService = mockk<PaymentService>(relaxUnitFun = true)
    private val pgClient = mockk<PgClient>()
    private val callbackUrl = "http://localhost:8080/api/v1/payments/callback"
    private val facade = PaymentFacade(userService, orderService, paymentService, pgClient, callbackUrl)

    private val loginId = "user1"
    private val rawPassword = "password1!"
    private val cardNo = "1234-5678-9814-1451"

    private fun user(id: Long = 1L): UserModel = mockk { every { this@mockk.id } returns id }

    private fun order(id: Long = 10L, status: OrderStatus = OrderStatus.PENDING, amount: Long = 5_000): OrderModel =
        mockk {
            every { this@mockk.id } returns id
            every { this@mockk.status } returns status
            every { finalAmount } returns amount
        }

    private fun payment(orderId: Long = 10L, key: String? = null): PaymentModel =
        PaymentModel.create(orderId, 1L, CardType.SAMSUNG, cardNo, 5_000)
            .also { if (key != null) it.assignTransactionKey(key) }

    @DisplayName("결제를 요청할 때,")
    @Nested
    inner class Pay {
        @DisplayName("정상 흐름이면, PG 접수 후 거래 키를 결제에 반영한다.")
        @Test
        fun assignsTransactionKey_onSuccess() {
            every { userService.authenticate(loginId, rawPassword) } returns user()
            every { orderService.getByIdAndUserId(10L, 1L) } returns order()
            every { paymentService.prepare(10L, 1L, CardType.SAMSUNG, cardNo, 5_000) } returns payment()
            every { pgClient.requestPayment(any()) } returns
                PgTransactionResult("TR-1", "000010", PgTransactionStatus.PENDING, null)
            every { paymentService.assignTransactionKey(10L, "TR-1") } returns payment(key = "TR-1")
            every { paymentService.getByOrderId(10L) } returns payment(key = "TR-1")

            val result = facade.pay(loginId, rawPassword, 10L, CardType.SAMSUNG, cardNo)

            verify(exactly = 1) { paymentService.assignTransactionKey(10L, "TR-1") }
            assertThat(result.transactionKey).isEqualTo("TR-1")
        }

        @DisplayName("주문이 PENDING 상태가 아니면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenOrderNotPending() {
            every { userService.authenticate(loginId, rawPassword) } returns user()
            every { orderService.getByIdAndUserId(10L, 1L) } returns order(status = OrderStatus.PAID)

            val result = assertThrows<CoreException> {
                facade.pay(loginId, rawPassword, 10L, CardType.SAMSUNG, cardNo)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
            verify(exactly = 0) { pgClient.requestPayment(any()) }
        }

        @DisplayName("PG 접수가 불가하면(PgUnavailable), 예외를 전파하지 않고 결제를 PENDING 으로 유지한다.")
        @Test
        fun keepsPending_whenPgUnavailable() {
            every { userService.authenticate(loginId, rawPassword) } returns user()
            every { orderService.getByIdAndUserId(10L, 1L) } returns order()
            every { paymentService.prepare(any(), any(), any(), any(), any()) } returns payment()
            every { pgClient.requestPayment(any()) } throws PgUnavailableException()
            every { paymentService.getByOrderId(10L) } returns payment()

            val result = facade.pay(loginId, rawPassword, 10L, CardType.SAMSUNG, cardNo)

            verify(exactly = 0) { paymentService.assignTransactionKey(any(), any()) }
            assertThat(result.transactionKey).isNull()
        }
    }

    @DisplayName("PG 콜백을 처리할 때,")
    @Nested
    inner class HandleCallback {
        @DisplayName("SUCCESS 콜백이면, 결제를 성공으로 확정하고 주문을 결제 완료로 전이한다.")
        @Test
        fun success_marksSuccessAndPaysOrder() {
            every { paymentService.markSuccess("TR-1") } returns payment(orderId = 10L, key = "TR-1")

            facade.handleCallback("TR-1", PgTransactionStatus.SUCCESS, null)

            verify(exactly = 1) { paymentService.markSuccess("TR-1") }
            verify(exactly = 1) { orderService.payIfPending(10L) }
        }

        @DisplayName("FAILED 콜백이면, 결제를 실패로 확정하고 주문은 전이하지 않는다.")
        @Test
        fun failed_marksFailedOnly() {
            every { paymentService.markFailed("TR-1", "한도초과") } returns payment(key = "TR-1")

            facade.handleCallback("TR-1", PgTransactionStatus.FAILED, "한도초과")

            verify(exactly = 1) { paymentService.markFailed("TR-1", "한도초과") }
            verify(exactly = 0) { orderService.payIfPending(any()) }
        }
    }

    @DisplayName("상태를 동기화할 때,")
    @Nested
    inner class Sync {
        @DisplayName("거래 키가 있는 PENDING 건이 PG 에서 SUCCESS면, 성공으로 확정한다.")
        @Test
        fun success_whenPgReturnsSuccess() {
            every { paymentService.getByOrderId(10L) } returns payment(orderId = 10L, key = "TR-1")
            every { pgClient.getTransaction("1", "TR-1") } returns
                PgTransactionResult("TR-1", "000010", PgTransactionStatus.SUCCESS, null)
            every { paymentService.markSuccess("TR-1") } returns payment(orderId = 10L, key = "TR-1")

            facade.sync(10L)

            verify(exactly = 1) { paymentService.markSuccess("TR-1") }
            verify(exactly = 1) { orderService.payIfPending(10L) }
        }

        @DisplayName("이미 확정된 건이면, PG 를 조회하지 않고 종료한다.")
        @Test
        fun skips_whenAlreadySettled() {
            val settled = payment(orderId = 10L, key = "TR-1").apply { markSuccess() }
            every { paymentService.getByOrderId(10L) } returns settled

            facade.sync(10L)

            verify(exactly = 0) { pgClient.getTransaction(any(), any()) }
        }
    }
}
