package com.loopers.application.payment

import com.loopers.application.order.CreateOrderCommand
import com.loopers.application.order.CreateOrderItemCommand
import com.loopers.application.order.OrderFacade
import com.loopers.domain.payment.PaymentRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaEntity
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.domain.user.EncodedPassword
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.LocalDate

@SpringBootTest
class PaymentFacadeIntegrationTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val orderFacade: OrderFacade,
    private val paymentRepository: PaymentRepository,
    private val fakePaymentGateway: FakePaymentGateway,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        fakePaymentGateway.reset()
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("결제 요청 시, ")
    @Nested
    inner class RequestPayment {
        @DisplayName("PENDING_PAYMENT 상태의 주문에 대해 PG에 결제를 요청하고 Payment를 저장한다.")
        @Test
        fun requestPayment_savesPayment_whenOrderIsPendingPayment() {
            // arrange
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

            // act
            val result = paymentFacade.requestPayment(
                RequestPaymentCommand(
                    orderId = order.id,
                    userId = user.id,
                    cardType = "SAMSUNG",
                    cardNo = "1234-5678-9012-3456",
                ),
            )

            // assert
            val savedPayment = paymentRepository.findByOrderId(order.id)!!
            assertAll(
                { assertThat(result.status).isEqualTo(PaymentStatus.PENDING) },
                { assertThat(result.transactionKey).isNotBlank() },
                { assertThat(savedPayment.orderId).isEqualTo(order.id) },
                { assertThat(savedPayment.cardType).isEqualTo("SAMSUNG") },
                { assertThat(savedPayment.amount).isEqualTo(10_000L) },
                { assertThat(savedPayment.status).isEqualTo(PaymentStatus.PENDING) },
            )
        }

        @DisplayName("존재하지 않는 주문에 대해 결제를 요청하면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun requestPayment_throwsNotFound_whenOrderDoesNotExist() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())

            // act & assert
            val result = assertThrows<CoreException> {
                paymentFacade.requestPayment(
                    RequestPaymentCommand(
                        orderId = 999L,
                        userId = user.id,
                        cardType = "SAMSUNG",
                        cardNo = "1234-5678-9012-3456",
                    ),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
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
