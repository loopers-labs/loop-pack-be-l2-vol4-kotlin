package com.loopers.interfaces.api

import com.loopers.application.order.CreateOrderCommand
import com.loopers.application.order.CreateOrderItemCommand
import com.loopers.application.order.OrderFacade
import com.loopers.application.payment.PaymentCancelCommand
import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentResult
import com.loopers.application.payment.PaymentStatus
import com.loopers.application.payment.PaymentTransactionInfo
import com.loopers.application.payment.RequestPaymentCommand
import com.loopers.application.stock.StockApplicationService
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.user.EncodedPassword
import com.loopers.infrastructure.payment.PgCallbackRequest
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaEntity
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentCallbackV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val orderFacade: OrderFacade,
    private val paymentFacade: PaymentFacade,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val stockApplicationService: StockApplicationService,
    private val fakePaymentGateway: FakePaymentGateway,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    companion object {
        private const val CALLBACK_ENDPOINT = "/api/v1/payments/callback"
    }

    @AfterEach
    fun tearDown() {
        fakePaymentGateway.reset()
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/payments/callback")
    @Nested
    inner class HandleCallback {
        @DisplayName("SUCCESS 콜백을 수신하면 Payment를 SUCCESS로, Order를 PAID로 변경한다.")
        @Test
        fun returnsSuccess_whenCallbackIsSuccess() {
            // arrange
            val (order, paymentInfo) = placeOrderAndRequestPayment()
            val callbackRequest = PgCallbackRequest(
                transactionKey = paymentInfo.transactionKey,
                orderId = order.id.toString(),
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
                amount = 10_000L,
                status = "SUCCESS",
                reason = "정상 승인되었습니다.",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                CALLBACK_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(callbackRequest),
                responseType,
            )

            // assert
            val payment = paymentRepository.findByTransactionKey(paymentInfo.transactionKey)!!
            val updatedOrder = orderRepository.find(order.id)!!
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(payment.status).isEqualTo(PaymentStatus.SUCCESS) },
                { assertThat(payment.reason).isEqualTo("정상 승인되었습니다.") },
                { assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAID) },
            )
        }

        @DisplayName("FAILED 콜백을 수신하면 Payment를 FAILED로, Order를 PAYMENT_FAILED로 변경하고 재고를 복구한다.")
        @Test
        fun returnsSuccess_whenCallbackIsFailed_andReleasesResources() {
            // arrange
            val (order, paymentInfo) = placeOrderAndRequestPaymentWithCoupon()
            val callbackRequest = PgCallbackRequest(
                transactionKey = paymentInfo.transactionKey,
                orderId = order.id.toString(),
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
                amount = 9_000L,
                status = "FAILED",
                reason = "잔액 부족",
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                CALLBACK_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(callbackRequest),
                responseType,
            )

            // assert
            val payment = paymentRepository.findByTransactionKey(paymentInfo.transactionKey)!!
            val updatedOrder = orderRepository.find(order.id)!!
            val remainingStock = stockApplicationService.getStock(1L)
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(payment.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(payment.reason).isEqualTo("잔액 부족") },
                { assertThat(updatedOrder.status).isEqualTo(OrderStatus.PAYMENT_FAILED) },
                { assertThat(remainingStock.quantity).isEqualTo(10) },
            )
        }

        @DisplayName("이미 처리된 결제에 대해 중복 콜백이 오면 409를 반환한다.")
        @Test
        fun returnsConflict_whenCallbackIsDuplicate() {
            // arrange
            val (order, paymentInfo) = placeOrderAndRequestPayment()
            val callbackRequest = PgCallbackRequest(
                transactionKey = paymentInfo.transactionKey,
                orderId = order.id.toString(),
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
                amount = 10_000L,
                status = "SUCCESS",
                reason = "정상 승인되었습니다.",
            )
            testRestTemplate.exchange(
                CALLBACK_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(callbackRequest),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                CALLBACK_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(callbackRequest),
                responseType,
            )

            // assert
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            )
        }

        @DisplayName("존재하지 않는 transactionKey로 콜백이 오면 404를 반환한다.")
        @Test
        fun returnsNotFound_whenTransactionKeyDoesNotExist() {
            // arrange
            val callbackRequest = PgCallbackRequest(
                transactionKey = "non-existent-key",
                orderId = "999",
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
                amount = 10_000L,
                status = "SUCCESS",
                reason = null,
            )

            // act
            val responseType = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
            val response = testRestTemplate.exchange(
                CALLBACK_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(callbackRequest),
                responseType,
            )

            // assert
            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    private fun placeOrderAndRequestPayment(): Pair<com.loopers.application.order.OrderInfo, com.loopers.application.payment.PaymentInfo> {
        val user = userJpaRepository.save(newUserJpaEntity())
        val product = saveProductWithStock(price = 10_000L, stock = 10)
        fakePaymentGateway.nextStatus = PaymentStatus.PENDING

        val order = orderFacade.placeOrder(
            CreateOrderCommand(
                userId = user.id,
                items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                userCouponId = null,
            ),
        )

        val paymentInfo = paymentFacade.requestPayment(
            RequestPaymentCommand(
                orderId = order.id,
                userId = user.id,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            ),
        )

        return order to paymentInfo
    }

    private fun placeOrderAndRequestPaymentWithCoupon(): Pair<com.loopers.application.order.OrderInfo, com.loopers.application.payment.PaymentInfo> {
        val user = userJpaRepository.save(newUserJpaEntity())
        val product = saveProductWithStock(price = 10_000L, stock = 10)
        val coupon = couponRepository.save(
            Coupon(name = "1000원 할인", policy = DiscountPolicy.FixedAmount(1_000L)),
        )
        val userCoupon = userCouponRepository.save(
            UserCoupon(userId = user.id, couponId = coupon.id!!),
        )
        fakePaymentGateway.nextStatus = PaymentStatus.PENDING

        val order = orderFacade.placeOrder(
            CreateOrderCommand(
                userId = user.id,
                items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                userCouponId = userCoupon.id,
            ),
        )

        val paymentInfo = paymentFacade.requestPayment(
            RequestPaymentCommand(
                orderId = order.id,
                userId = user.id,
                cardType = "SAMSUNG",
                cardNo = "1234-5678-9012-3456",
            ),
        )

        return order to paymentInfo
    }

    private fun saveProductWithStock(
        brandId: Long = 1L,
        name: String = "Loopers T-Shirt",
        description: String = "매일 입기 좋은 티셔츠",
        price: Long = 10_000L,
        stock: Int = 10,
    ): ProductJpaEntity {
        val product = productJpaRepository.save(
            ProductJpaEntity(
                brandId = brandId,
                name = name,
                description = description,
                price = price,
            ),
        )
        stockJpaRepository.save(StockJpaEntity(productId = product.id, quantity = stock))
        return product
    }

    private fun newUserJpaEntity(
        loginId: String = "seondays",
        password: String = "\$2a\$10\$existingHashedPassword.",
        name: String = "선데이",
        birthDate: LocalDate = LocalDate.of(1990, 1, 1),
        email: String = "seondays@example.com",
    ) = UserJpaEntity(
        loginId = loginId,
        encodedPassword = EncodedPassword(password),
        name = name,
        birthDate = birthDate,
        email = email,
    )

    @TestConfiguration
    class PaymentTestConfiguration {
        @Bean
        @Primary
        fun fakePaymentGateway(): FakePaymentGateway {
            return FakePaymentGateway()
        }
    }

    class FakePaymentGateway : PaymentGateway {
        var nextStatus: PaymentStatus = PaymentStatus.PENDING
        var lastCommand: PaymentCommand? = null
        val cancelCommands: MutableList<PaymentCancelCommand> = mutableListOf()

        override fun pay(command: PaymentCommand): PaymentResult {
            lastCommand = command
            return PaymentResult(
                transactionKey = "fake:TR:${System.currentTimeMillis()}",
                status = nextStatus,
                reason = null,
            )
        }

        override fun cancel(command: PaymentCancelCommand) {
            cancelCommands.add(command)
        }

        override fun getTransactionStatus(transactionKey: String): PaymentTransactionInfo {
            throw UnsupportedOperationException()
        }

        fun reset() {
            nextStatus = PaymentStatus.PENDING
            lastCommand = null
            cancelCommands.clear()
        }
    }
}
