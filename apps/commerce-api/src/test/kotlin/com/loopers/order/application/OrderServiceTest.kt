package com.loopers.order.application

import com.loopers.coupon.application.CouponService
import com.loopers.inventory.domain.Inventory
import com.loopers.inventory.domain.InventoryErrorCode
import com.loopers.inventory.domain.InventoryRepository
import com.loopers.order.domain.Order
import com.loopers.order.domain.OrderErrorCode
import com.loopers.order.domain.OrderItemSnapshot
import com.loopers.order.domain.OrderRepository
import com.loopers.order.domain.OrderStatus
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductErrorCode
import com.loopers.product.domain.ProductName
import com.loopers.product.domain.ProductRepository
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OrderServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val productRepository: ProductRepository = mock()
    private val inventoryRepository: InventoryRepository = mock()
    private val couponService: CouponService = mock()
    private val orderService = OrderService(orderRepository, productRepository, inventoryRepository, couponService)

    private fun product() = Product(brandId = 10L, name = ProductName("에어맥스"), price = Money(100_000))

    private fun ownedOrder(userId: Long = 1L) =
        Order.create(userId, listOf(OrderItemSnapshot(1L, 10L, "에어맥스", "나이키", Money(1000), 1)))

    private fun command(
        items: List<OrderLineCommand>,
        couponId: Long? = null,
        expectedOriginalAmount: Long,
        expectedDiscountAmount: Long = 0,
        expectedTotalAmount: Long = expectedOriginalAmount - expectedDiscountAmount,
    ) = OrderCreateCommand(
        items = items,
        couponId = couponId,
        expectedOriginalAmount = expectedOriginalAmount,
        expectedDiscountAmount = expectedDiscountAmount,
        expectedTotalAmount = expectedTotalAmount,
    )

    @DisplayName("주문을 생성하면, 재고를 차감하고 PENDING_PAYMENT 주문을 박제 저장한다.")
    @Test
    fun placesOrder_decreasesInventory_andSavesPendingPayment() {
        val product = product()
        val inventory = Inventory.createFor(product.id, 100)
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id))).thenReturn(listOf(product))
        whenever(inventoryRepository.findAllByProductIdInForUpdate(listOf(product.id))).thenReturn(listOf(inventory))
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] as Order }

        val info = orderService.place(
            userId = 1L,
            command = command(
                items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                expectedOriginalAmount = 200_000,
            ),
        )

        assertAll(
            { assertThat(inventory.quantity).isEqualTo(98) },
            { assertThat(info.status).isEqualTo(OrderStatus.PENDING_PAYMENT) },
            { assertThat(info.originalAmount).isEqualTo(200_000) },
            { assertThat(info.discountAmount).isEqualTo(0) },
            { assertThat(info.totalAmount).isEqualTo(200_000) },
            { assertThat(info.items).hasSize(1) },
            { assertThat(info.userId).isEqualTo(1L) },
        )
        verify(orderRepository).save(any())
    }

    @DisplayName("쿠폰을 적용하면, 쿠폰을 사용 처리하고 할인이 반영된 금액으로 박제한다.")
    @Test
    fun placesOrder_withCoupon_appliesDiscount() {
        val product = product()
        val inventory = Inventory.createFor(product.id, 100)
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id))).thenReturn(listOf(product))
        whenever(inventoryRepository.findAllByProductIdInForUpdate(listOf(product.id))).thenReturn(listOf(inventory))
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] as Order }
        whenever(couponService.use(eq(1L), eq(5L), eq(Money(200_000)), any())).thenReturn(Money(10_000))

        val info = orderService.place(
            userId = 1L,
            command = command(
                items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                couponId = 5L,
                expectedOriginalAmount = 200_000,
                expectedDiscountAmount = 10_000,
            ),
        )

        assertAll(
            { assertThat(info.originalAmount).isEqualTo(200_000) },
            { assertThat(info.discountAmount).isEqualTo(10_000) },
            { assertThat(info.totalAmount).isEqualTo(190_000) },
            { assertThat(info.couponId).isEqualTo(5L) },
        )
        verify(couponService).use(eq(1L), eq(5L), eq(Money(200_000)), any())
    }

    @DisplayName("계산서 금액(expected 3종)이 재계산값과 다르면, CONFLICT(PRICE_CHANGED) 예외가 발생하고 재고를 건드리지 않는다.")
    @Test
    fun throwsConflict_whenExpectedAmountMismatch() {
        val product = product()
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id))).thenReturn(listOf(product))

        val result = assertThrows<ConflictException> {
            orderService.place(
                userId = 1L,
                command = command(
                    items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                    expectedOriginalAmount = 180_000,
                ),
            )
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(OrderErrorCode.PRICE_CHANGED) },
            { verify(inventoryRepository, never()).findAllByProductIdInForUpdate(any()) },
            { verify(orderRepository, never()).save(any()) },
        )
    }

    @DisplayName("총액은 같아도 원금·할인 구성이 다르면, CONFLICT(PRICE_CHANGED) 예외가 발생한다. (상쇄 변동 차단)")
    @Test
    fun throwsConflict_whenAmountsOffsetEachOther() {
        val product = product()
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id))).thenReturn(listOf(product))
        whenever(couponService.use(eq(1L), eq(5L), eq(Money(200_000)), any())).thenReturn(Money(10_000))

        val result = assertThrows<ConflictException> {
            orderService.place(
                userId = 1L,
                command = command(
                    items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                    couponId = 5L,
                    expectedOriginalAmount = 199_000,
                    expectedDiscountAmount = 9_000,
                    expectedTotalAmount = 190_000,
                ),
            )
        }

        assertThat(result.errorCode).isEqualTo(OrderErrorCode.PRICE_CHANGED)
    }

    @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenItemsEmpty() {
        val result = assertThrows<BadRequestException> {
            orderService.place(userId = 1L, command = command(items = emptyList(), expectedOriginalAmount = 0))
        }

        assertThat(result.errorCode).isEqualTo(OrderErrorCode.EMPTY_ORDER_ITEMS)
    }

    @DisplayName("존재하지 않는 상품이 포함되면, NOT_FOUND(상품) 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenProductMissing() {
        whenever(productRepository.findAllActiveByIdIn(listOf(99L))).thenReturn(emptyList())

        val result = assertThrows<NotFoundException> {
            orderService.place(
                userId = 1L,
                command = command(
                    items = listOf(OrderLineCommand(productId = 99L, quantity = 1)),
                    expectedOriginalAmount = 100_000,
                ),
            )
        }

        assertThat(result.errorCode).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND)
    }

    @DisplayName("재고가 부족하면, CONFLICT(재고 부족) 예외가 발생한다.")
    @Test
    fun throwsConflict_whenStockInsufficient() {
        val product = product()
        val inventory = Inventory.createFor(product.id, 1)
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id))).thenReturn(listOf(product))
        whenever(inventoryRepository.findAllByProductIdInForUpdate(listOf(product.id))).thenReturn(listOf(inventory))

        val result = assertThrows<ConflictException> {
            orderService.place(
                userId = 1L,
                command = command(
                    items = listOf(OrderLineCommand(productId = product.id, quantity = 5)),
                    expectedOriginalAmount = 500_000,
                ),
            )
        }

        assertThat(result.errorCode).isEqualTo(InventoryErrorCode.STOCK_INSUFFICIENT)
    }

    @DisplayName("상품 재고 행이 없으면, NOT_FOUND(재고) 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenInventoryMissing() {
        val product = product()
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id))).thenReturn(listOf(product))
        whenever(inventoryRepository.findAllByProductIdInForUpdate(listOf(product.id))).thenReturn(emptyList())

        val result = assertThrows<NotFoundException> {
            orderService.place(
                userId = 1L,
                command = command(
                    items = listOf(OrderLineCommand(productId = product.id, quantity = 1)),
                    expectedOriginalAmount = 100_000,
                ),
            )
        }

        assertThat(result.errorCode).isEqualTo(InventoryErrorCode.INVENTORY_NOT_FOUND)
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
