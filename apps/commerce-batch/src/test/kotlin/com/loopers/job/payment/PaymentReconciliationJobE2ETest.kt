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

        @Test
        fun `요청 타임아웃으로 txKey 없는 PENDING 결제를 orderId 조회로 복구한다`() {
            // Arrange: 실제 주문 생성 (재고 차감 포함)
            val loginId = "batchtest" + UUID.randomUUID().toString().replace("-", "").take(8)
            val password = "Password1!"
            val user =
                userService.signUp(
                    UserService.SignUpCommand(
                        loginId = loginId,
                        password = password,
                        name = "배치테스터",
                        birthDate = LocalDate.of(1990, 1, 1),
                        email = "$loginId@loopers.com",
                    ),
                )
            val product =
                productRepository.save(
                    ProductModel(brandId = 1L, name = "배치상품", description = "설명", price = BigDecimal("10000")),
                )
            productStockRepository.save(ProductStockModel(productId = product.id, quantity = 10))

            val order =
                createOrderUsecase.execute(
                    OrderCommand(
                        loginId = loginId,
                        password = password,
                        items = listOf(OrderCommand.OrderItemCommand(productId = product.id, quantity = 1)),
                        couponId = null,
                    ),
                )

            // txKey 없는 PENDING 결제 저장 (요청 타임아웃 상태)
            paymentRepository.save(
                PaymentModel(
                    orderId = order.id,
                    userId = user.id,
                    amount = order.paidPrice,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9012-3456",
                ),
            )

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

            // Act: 임계 0초로 즉시 대상 포함
            val reflected = tasklet.reconcile(0L)

            // Assert: 반영 건수, txKey 채워짐, 결제 SUCCESS, 주문 PAID
            assertThat(reflected).isEqualTo(1)
            val reloaded = paymentRepository.findByOrderId(order.id)
            assertThat(reloaded?.transactionKey).isEqualTo("tx-found")
            assertThat(reloaded?.status).isEqualTo(PaymentStatus.SUCCESS)
            assertThat(orderRepository.findById(order.id)?.status).isEqualTo(OrderStatus.PAID)
        }
    }
