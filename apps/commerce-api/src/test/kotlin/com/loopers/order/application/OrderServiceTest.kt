package com.loopers.order.application

import com.loopers.order.domain.Order
import com.loopers.order.domain.OrderErrorCode
import com.loopers.order.domain.OrderItemSnapshot
import com.loopers.order.domain.OrderRepository
import com.loopers.order.domain.OrderStatus
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductErrorCode
import com.loopers.product.domain.ProductName
import com.loopers.shared.domain.Money
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OrderServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val orderService = OrderService(orderRepository)

    private fun product() = Product(brandId = 10L, name = ProductName("에어맥스"), price = Money(100_000))

    private fun ownedOrder(userId: Long = 1L) =
        Order.create(userId, listOf(OrderItemSnapshot(1L, 10L, "에어맥스", "나이키", Money(1000), 1)))

    private fun command(
        items: List<OrderLineCommand>,
        couponId: Long? = null,
        expectedOriginalAmount: Long,
        expectedDiscountAmount: Long = 0,
        expectedTotalAmount: Long = expectedOriginalAmount - expectedDiscountAmount,
        userId: Long = 1L,
    ) = OrderCreateCommand(
        userId = userId,
        items = items,
        couponId = couponId,
        expectedOriginalAmount = expectedOriginalAmount,
        expectedDiscountAmount = expectedDiscountAmount,
        expectedTotalAmount = expectedTotalAmount,
    )

    @DisplayName("주문을 생성하면, 스냅샷을 박제하고 PENDING_PAYMENT 상태로 저장한다.")
    @Test
    fun createsOrder_withSnapshots_andPendingPayment() {
        val product = product()
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] as Order }

        val info = orderService.create(
            command = command(
                items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                expectedOriginalAmount = 200_000,
            ),
            products = mapOf(product.id to product),
            discountAmount = Money(0),
        )

        assertAll(
            { assertThat(info.status).isEqualTo(OrderStatus.PENDING_PAYMENT) },
            { assertThat(info.originalAmount).isEqualTo(200_000) },
            { assertThat(info.discountAmount).isEqualTo(0) },
            { assertThat(info.totalAmount).isEqualTo(200_000) },
            { assertThat(info.items).hasSize(1) },
            { assertThat(info.userId).isEqualTo(1L) },
        )
        verify(orderRepository).save(any())
    }

    @DisplayName("할인액이 주어지면, 할인이 반영된 금액으로 박제한다.")
    @Test
    fun createsOrder_withDiscountApplied() {
        val product = product()
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] as Order }

        val info = orderService.create(
            command = command(
                items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                couponId = 5L,
                expectedOriginalAmount = 200_000,
                expectedDiscountAmount = 10_000,
            ),
            products = mapOf(product.id to product),
            discountAmount = Money(10_000),
        )

        assertAll(
            { assertThat(info.originalAmount).isEqualTo(200_000) },
            { assertThat(info.discountAmount).isEqualTo(10_000) },
            { assertThat(info.totalAmount).isEqualTo(190_000) },
            { assertThat(info.couponId).isEqualTo(5L) },
        )
    }

    @DisplayName("계산서 금액(expected 3종)이 재계산값과 다르면, CONFLICT(PRICE_CHANGED) 예외가 발생하고 저장하지 않는다.")
    @Test
    fun throwsConflict_whenExpectedAmountMismatch() {
        val product = product()

        val result = assertThrows<ConflictException> {
            orderService.create(
                command = command(
                    items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                    expectedOriginalAmount = 180_000,
                ),
                products = mapOf(product.id to product),
                discountAmount = Money(0),
            )
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(OrderErrorCode.PRICE_CHANGED) },
            { verify(orderRepository, never()).save(any()) },
        )
    }

    @DisplayName("총액은 같아도 원금·할인 구성이 다르면, CONFLICT(PRICE_CHANGED) 예외가 발생한다. (상쇄 변동 차단)")
    @Test
    fun throwsConflict_whenAmountsOffsetEachOther() {
        val product = product()

        val result = assertThrows<ConflictException> {
            orderService.create(
                command = command(
                    items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                    couponId = 5L,
                    expectedOriginalAmount = 199_000,
                    expectedDiscountAmount = 9_000,
                    expectedTotalAmount = 190_000,
                ),
                products = mapOf(product.id to product),
                discountAmount = Money(10_000),
            )
        }

        assertThat(result.errorCode).isEqualTo(OrderErrorCode.PRICE_CHANGED)
    }

    @DisplayName("상품 맵에 없는 상품이 포함되면, NOT_FOUND(상품) 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenProductMissing() {
        val result = assertThrows<NotFoundException> {
            orderService.create(
                command = command(
                    items = listOf(OrderLineCommand(productId = 99L, quantity = 1)),
                    expectedOriginalAmount = 100_000,
                ),
                products = emptyMap(),
                discountAmount = Money(0),
            )
        }

        assertThat(result.errorCode).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND)
    }

    @DisplayName("본인 주문은 단건 조회할 수 있다.")
    @Test
    fun findsOwnOrder() {
        whenever(orderRepository.findById(10L)).thenReturn(ownedOrder(userId = 1L))

        val info = orderService.findById(orderId = 10L, requesterUserId = 1L)

        assertThat(info.userId).isEqualTo(1L)
    }

    @DisplayName("타 사용자의 주문을 조회하면, NOT_FOUND 예외가 발생한다. (ID enumeration 차단)")
    @Test
    fun throwsNotFound_whenOrderBelongsToOther() {
        whenever(orderRepository.findById(10L)).thenReturn(ownedOrder(userId = 1L))

        val result = assertThrows<NotFoundException> {
            orderService.findById(orderId = 10L, requesterUserId = 999L)
        }

        assertThat(result.errorCode).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND)
    }

    @DisplayName("존재하지 않는 주문을 조회하면, NOT_FOUND 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenOrderMissing() {
        whenever(orderRepository.findById(99L)).thenReturn(null)

        val result = assertThrows<NotFoundException> {
            orderService.findById(orderId = 99L, requesterUserId = 1L)
        }

        assertThat(result.errorCode).isEqualTo(OrderErrorCode.ORDER_NOT_FOUND)
    }
}
