package com.loopers.job.payment

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.loopers.CommerceBatchApplication
import com.loopers.application.order.OrderCommand
import com.loopers.application.order.usecase.CreateOrderUsecase
import com.loopers.batch.job.payment.step.PaymentReconciliationTasklet
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(classes = [CommerceBatchApplication::class])
@AutoConfigureWireMock(port = 0)
@TestPropertySource(
    properties = [
        "pg.base-url=http://localhost:\${wiremock.server.port}",
        // job.name 으로 JobConfig+Tasklet 빈 활성화, job.enabled=false 로 시작 시 자동 실행 방지(직접 reconcile 호출).
        "spring.batch.job.name=paymentReconciliationJob",
        "spring.batch.job.enabled=false",
    ],
)
class PaymentReconciliationJobE2ETest
    @Autowired
    constructor(
        private val tasklet: PaymentReconciliationTasklet,
        private val paymentRepository: PaymentRepository,
        private val orderRepository: OrderRepository,
        private val userService: UserService,
        private val productRepository: ProductRepository,
        private val productStockRepository: ProductStockRepository,
        private val createOrderUsecase: CreateOrderUsecase,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() = databaseCleanUp.truncateAllTables()

        data class Fixture(val userId: Long, val orderId: Long, val paymentId: Long, val productId: Long)

        private fun createUserOrderPayment(
            loginId: String,
            stockQty: Int = 10,
            orderQty: Int = 1,
        ): Fixture {
            val user =
                userService.signUp(
                    UserService.SignUpCommand(
                        loginId = loginId,
                        password = "Password1!",
                        name = "배치테스터",
                        birthDate = LocalDate.of(1990, 1, 1),
                        email = "$loginId@loopers.com",
                    ),
                )
            val product =
                productRepository.save(
                    ProductModel(brandId = 1L, name = "배치상품", description = "설명", price = BigDecimal("10000")),
                )
            productStockRepository.save(ProductStockModel(productId = product.id, quantity = stockQty))

            val order =
                createOrderUsecase.execute(
                    OrderCommand(
                        loginId = loginId,
                        password = "Password1!",
                        items = listOf(OrderCommand.OrderItemCommand(productId = product.id, quantity = orderQty)),
                        couponId = null,
                    ),
                )

            val payment =
                paymentRepository.save(
                    PaymentModel(
                        orderId = order.id,
                        userId = user.id,
                        amount = order.paidPrice,
                        cardType = CardType.SAMSUNG,
                        cardNo = "1234-5678-9012-3456",
                    ),
                )
            return Fixture(user.id, order.id, payment.id, product.id)
        }

        @Test
        fun `요청 타임아웃으로 txKey 없는 PENDING 결제를 orderId 조회로 복구한다`() {
            // Arrange
            val loginId = "batchtest" + UUID.randomUUID().toString().replace("-", "").take(8)
            val (_, orderId, _, _) = createUserOrderPayment(loginId)

            // WireMock: GET /api/v1/payments?orderId=<id> → SUCCESS with tx-found
            stubFor(
                get(urlPathEqualTo("/api/v1/payments")).willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"meta":{"result":"SUCCESS"},"data":{"transactions":[{"transactionKey":"tx-found","status":"SUCCESS"}]}}""",
                        ),
                ),
            )

            // Act: 임계 0초로 즉시 대상 포함, tMax=600 (should not expire)
            val reflected = tasklet.reconcile(0L, 600L)

            // Assert
            assertThat(reflected).isEqualTo(1)
            val reloaded = paymentRepository.findByOrderId(orderId)
            assertThat(reloaded?.transactionKey).isEqualTo("tx-found")
            assertThat(reloaded?.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(orderRepository.findById(orderId)?.status).isEqualTo(OrderStatus.PAID)
        }

        @Test
        fun `PG 미접수 확정(NotAccepted)이면 결제 FAILED(NOT_ACCEPTED) 로 종결하고 재고를 복구한다`() {
            // Arrange
            val loginId = "batchtest" + UUID.randomUUID().toString().replace("-", "").take(8)
            val (_, orderId, _, productId) = createUserOrderPayment(loginId, stockQty = 10, orderQty = 1)

            // 주문 후 재고: 10 - 1 = 9 남음
            val stockBefore = productStockRepository.findByProductId(productId)!!.quantity

            // WireMock: PG가 빈 transactions 배열 반환 → NotAccepted
            stubFor(
                get(urlPathEqualTo("/api/v1/payments")).willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"meta":{"result":"SUCCESS"},"data":{"transactions":[]}}""",
                        ),
                ),
            )

            // Act
            val reflected = tasklet.reconcile(0L, 600L)

            // Assert: 결제 FAILED(NOT_ACCEPTED), 주문 FAILED, 재고 복구
            assertThat(reflected).isEqualTo(1)
            val payment = paymentRepository.findByOrderId(orderId)!!
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureReason).isEqualTo(PaymentFailureReason.NOT_ACCEPTED)
            assertThat(orderRepository.findById(orderId)?.status).isEqualTo(OrderStatus.FAILED)
            val stockAfter = productStockRepository.findByProductId(productId)!!.quantity
            // 재고가 복구되어야 함 (stockBefore + 1)
            assertThat(stockAfter).isEqualTo(stockBefore + 1)
        }

        @Test
        fun `tMax 초과 불명(Unknown) 상태 결제는 payment만 FAILED(UNRESOLVED)로 격리하고 주문은 PENDING 유지한다`() {
            // Arrange
            val loginId = "batchtest" + UUID.randomUUID().toString().replace("-", "").take(8)
            val (_, orderId, _, _) = createUserOrderPayment(loginId)

            // WireMock: FALLBACK 응답 → Unknown
            stubFor(
                get(urlPathEqualTo("/api/v1/payments")).willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{"meta":{"result":"FALLBACK"},"data":null}""",
                        ),
                ),
            )

            // Act: tMax=0 → 즉시 tMax 초과
            val reflected = tasklet.reconcile(0L, 0L)

            // Assert: payment FAILED(UNRESOLVED), 주문은 여전히 PENDING (보상/전이 없음)
            assertThat(reflected).isEqualTo(1)
            val payment = paymentRepository.findByOrderId(orderId)!!
            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureReason).isEqualTo(PaymentFailureReason.UNRESOLVED)
            assertThat(orderRepository.findById(orderId)?.status).isEqualTo(OrderStatus.PENDING)
        }
    }
