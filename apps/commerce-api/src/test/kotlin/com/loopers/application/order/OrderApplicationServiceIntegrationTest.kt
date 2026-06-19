package com.loopers.application.order

import com.loopers.domain.order.OrderAmount
import com.loopers.domain.order.OrderAmountCalculator
import com.loopers.domain.order.OrderAmounts
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderItemPrice
import com.loopers.domain.order.OrderQuantity
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.ProductSnapshot
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
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

@SpringBootTest
class OrderApplicationServiceIntegrationTest @Autowired constructor(
    private val orderApplicationService: OrderApplicationService,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주문 생성 시, ")
    @Nested
    inner class CreateOrder {
        @DisplayName("유저 ID와 주문 상품이 유효하면 주문을 저장한다.")
        @Test
        fun createOrder_whenAllFieldsAreValid() {
            // arrange
            val items = listOf(newOrderItem(productId = 10L, quantity = OrderQuantity(2)))

            // act
            val order = createOrder(userId = 1L, items = items)

            // assert
            assertAll(
                { assertThat(order.id).isNotNull() },
                { assertThat(order.userId).isEqualTo(1L) },
                { assertThat(order.userCouponId).isNull() },
                { assertThat(order.status).isEqualTo(OrderStatus.PENDING_PAYMENT) },
                { assertThat(order.totalAmount.amount).isEqualTo(20_000L) },
                { assertThat(order.discountAmount.amount).isEqualTo(0L) },
                { assertThat(order.paymentAmount.amount).isEqualTo(20_000L) },
                { assertThat(order.items).hasSize(1) },
            )
        }

        @DisplayName("주어진 주문 금액 정보를 함께 저장한다.")
        @Test
        fun createOrder_persistsAmounts() {
            // arrange
            val items = listOf(newOrderItem(productId = 10L, quantity = OrderQuantity(2)))
            val amounts = OrderAmounts.of(
                totalAmount = OrderAmount(20_000L),
                discountAmount = OrderAmount(1_000L),
            )

            // act
            val order = createOrder(userId = 1L, items = items, amounts = amounts)

            // assert - DB에서 재조회해 금액이 실제로 영속됐는지 확인
            val fetched = orderApplicationService.getOrder(order.id!!)
            assertAll(
                { assertThat(fetched.totalAmount.amount).isEqualTo(20_000L) },
                { assertThat(fetched.discountAmount.amount).isEqualTo(1_000L) },
                { assertThat(fetched.paymentAmount.amount).isEqualTo(19_000L) },
            )
        }

        @DisplayName("주어진 발급 쿠폰 ID를 함께 저장한다.")
        @Test
        fun createOrder_persistsUserCouponId() {
            // arrange
            val items = listOf(newOrderItem(productId = 10L, quantity = OrderQuantity(2)))

            // act
            val order = createOrder(userId = 1L, userCouponId = 100L, items = items)

            // assert
            val fetched = orderApplicationService.getOrder(order.id!!)
            val entity = orderJpaRepository.findWithItemsByIdAndDeletedAtIsNull(order.id!!)
            assertAll(
                { assertThat(order.userCouponId).isEqualTo(100L) },
                { assertThat(fetched.userCouponId).isEqualTo(100L) },
                { assertThat(entity?.userCouponId).isEqualTo(100L) },
            )
        }
    }

    @DisplayName("주문 조회 시, ")
    @Nested
    inner class GetOrder {
        @DisplayName("존재하는 주문 ID이면 주문 상품과 함께 반환한다.")
        @Test
        fun getOrder_whenOrderExists() {
            // arrange
            val saved = createOrder(
                userId = 1L,
                items = listOf(newOrderItem(productId = 10L, quantity = OrderQuantity(2))),
            )

            // act
            val order = orderApplicationService.getOrder(saved.id!!)

            // assert
            assertAll(
                { assertThat(order.id).isEqualTo(saved.id) },
                { assertThat(order.items).hasSize(1) },
                { assertThat(order.items.first().productId).isEqualTo(10L) },
                { assertThat(order.items.first().productName).isEqualTo("Loopers T-Shirt") },
            )
        }

        @DisplayName("존재하지 않는 주문 ID이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenOrderDoesNotExist() {
            // act & assert
            val result = assertThrows<CoreException> {
                orderApplicationService.getOrder(999L)
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("주문 상태 변경 시, ")
    @Nested
    inner class ChangeStatus {
        @DisplayName("결제 완료 상태로 변경할 수 있다.")
        @Test
        fun markPaid() {
            // arrange
            val saved = createOrder(userId = 1L, items = listOf(newOrderItem()))

            // act
            val result = orderApplicationService.markPaid(saved.id!!)

            // assert
            assertThat(result).isInstanceOf(OrderConfirmResult.Confirmed::class.java)
            assertThat((result as OrderConfirmResult.Confirmed).order.status).isEqualTo(OrderStatus.PAID)
        }

        @DisplayName("결제 실패 상태로 변경할 수 있다.")
        @Test
        fun markPaymentFailed() {
            // arrange
            val saved = createOrder(userId = 1L, items = listOf(newOrderItem()))

            // act
            val order = orderApplicationService.markPaymentFailed(saved.id!!)

            // assert
            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        }

        @DisplayName("취소 상태로 변경할 수 있다.")
        @Test
        fun cancelOrder() {
            // arrange
            val saved = createOrder(userId = 1L, items = listOf(newOrderItem()))

            // act
            val order = orderApplicationService.cancelOrder(saved.id!!)

            // assert
            assertThat(order.status).isEqualTo(OrderStatus.CANCELED)
        }
    }

    @DisplayName("주문 저장 시, ")
    @Nested
    inner class SaveOrder {
        @DisplayName("주문과 주문 상품을 함께 저장한다.")
        @Test
        fun saveOrder_savesOrderItems() {
            // arrange
            val items = listOf(
                newOrderItem(productId = 10L),
                newOrderItem(productId = 20L),
            )

            // act
            val order = createOrder(userId = 1L, items = items)

            // assert
            val entity = orderJpaRepository.findWithItemsByIdAndDeletedAtIsNull(order.id!!)
            assertThat(entity?.items).hasSize(2)
        }
    }

    private fun newOrderItem(
        productId: Long = 10L,
        productName: String = "Loopers T-Shirt",
        productPrice: OrderItemPrice = OrderItemPrice(10_000L),
        quantity: OrderQuantity = OrderQuantity(1),
    ) = OrderItem(
        productSnapshot = ProductSnapshot(
            productId = productId,
            productName = productName,
            productPrice = productPrice,
        ),
        quantity = quantity,
    )

    private fun createOrder(
        userId: Long,
        userCouponId: Long? = null,
        items: List<OrderItem>,
        amounts: OrderAmounts = OrderAmountCalculator.calculate(items),
    ) = orderApplicationService.createOrder(
        userId = userId,
        userCouponId = userCouponId,
        items = items,
        amounts = amounts,
    )
}
