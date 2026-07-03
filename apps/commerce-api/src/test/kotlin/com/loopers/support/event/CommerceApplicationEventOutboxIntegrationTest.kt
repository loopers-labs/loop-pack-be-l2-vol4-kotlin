package com.loopers.support.event

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.order.application.OrderFacade
import com.loopers.domain.order.support.OrderSteps.Companion.주문_생성_커맨드
import com.loopers.domain.order.support.OrderSteps.Companion.주문항목_생성_커맨드
import com.loopers.domain.payment.application.service.PaymentService
import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.support.outbox.OutboxRepository
import com.loopers.support.outbox.event.CommerceOutboxEventType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = ["commerce-events.outbox-relay.enabled=false"],
)
class CommerceApplicationEventOutboxIntegrationTest
    @Autowired
    constructor(
        private val userService: UserService,
        private val brandService: BrandService,
        private val productFacade: ProductFacade,
        private val orderFacade: OrderFacade,
        private val paymentService: PaymentService,
        private val outboxRepository: OutboxRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `상품_상세_조회는_커밋_이후_product_viewed_outbox를_남긴다`() {
            val product = registerProduct()

            productFacade.getProduct(product.id)

            val events = outboxRepository.findPendingByType(CommerceOutboxEventType.PRODUCT_VIEWED_V1.name)
            assertThat(events).hasSize(1)
            assertThat(events.single().aggregateId).isEqualTo(product.id)
            assertThat(events.single().payload).contains(""""productId":${product.id}""")
        }

        @Test
        fun `새_주문은_order_created_outbox를_남기고_멱등_재요청은_중복_이벤트를_만들지_않는다`() {
            val user = userService.signUp(사용자_회원가입())
            val product = registerProduct(initialStock = 10)
            val command = 주문_생성_커맨드(
                userId = user.id,
                idempotencyKey = "order-event-key-1",
                items = listOf(주문항목_생성_커맨드(productId = product.id, quantity = 2)),
            )

            val first = orderFacade.placeOrder(command)
            val second = orderFacade.placeOrder(command)

            val events = outboxRepository.findPendingByType(CommerceOutboxEventType.ORDER_CREATED_V1.name)
            assertThat(second.id).isEqualTo(first.id)
            assertThat(events).hasSize(1)
            assertThat(events.single().aggregateId).isEqualTo(first.id)
        }

        @Test
        fun `결제_승인은_상품별_수량을_담은_order_paid_outbox를_남긴다`() {
            val user = userService.signUp(사용자_회원가입())
            val product = registerProduct(initialStock = 10)
            val order = orderFacade.placeOrder(
                주문_생성_커맨드(
                    userId = user.id,
                    items = listOf(주문항목_생성_커맨드(productId = product.id, quantity = 3)),
                ),
            )
            paymentService.request(order.id)
            paymentService.assignTransactionKey(order.id, "tx-order-paid-1")

            paymentService.approveByTransactionKey("tx-order-paid-1")

            val events = outboxRepository.findPendingByType(CommerceOutboxEventType.ORDER_PAID_V1.name)
            assertThat(events).hasSize(1)
            assertThat(events.single().aggregateId).isEqualTo(order.id)
            assertThat(events.single().payload).contains(
                """"orderId":${order.id}""",
                """"productId":${product.id}""",
                """"quantity":3""",
            )
        }

        private fun registerProduct(initialStock: Long = 10): com.loopers.domain.product.application.info.ProductInfo {
            val brand = brandService.register(브랜드_등록_커맨드())
            return productFacade.registerProduct(
                상품_등록_커맨드(
                    brandId = brand.id,
                    initialStock = initialStock,
                ),
            )
        }
    }
