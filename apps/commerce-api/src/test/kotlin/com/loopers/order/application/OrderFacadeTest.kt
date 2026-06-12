package com.loopers.order.application

import com.loopers.coupon.application.CouponService
import com.loopers.inventory.application.InventoryService
import com.loopers.inventory.application.StockDecreaseLine
import com.loopers.order.domain.OrderErrorCode
import com.loopers.order.domain.OrderStatus
import com.loopers.product.application.ProductService
import com.loopers.product.domain.Product
import com.loopers.product.domain.ProductErrorCode
import com.loopers.product.domain.ProductName
import com.loopers.shared.domain.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class OrderFacadeTest {
    private val productService: ProductService = mock()
    private val couponService: CouponService = mock()
    private val inventoryService: InventoryService = mock()
    private val orderService: OrderService = mock()
    private val orderFacade = OrderFacade(productService, couponService, inventoryService, orderService)

    private fun product() = Product(brandId = 10L, name = ProductName("에어맥스"), price = Money(100_000))

    private fun command(
        items: List<OrderLineCommand>,
        couponId: Long? = null,
        expectedOriginalAmount: Long,
        expectedDiscountAmount: Long = 0,
    ) = OrderCreateCommand(
        items = items,
        couponId = couponId,
        expectedOriginalAmount = expectedOriginalAmount,
        expectedDiscountAmount = expectedDiscountAmount,
        expectedTotalAmount = expectedOriginalAmount - expectedDiscountAmount,
    )

    private fun orderInfo(discountAmount: Long = 0, couponId: Long? = null) = OrderInfo(
        id = 1L,
        userId = 1L,
        orderedAt = LocalDateTime.of(2026, 6, 12, 17, 0),
        originalAmount = 200_000,
        discountAmount = discountAmount,
        totalAmount = 200_000 - discountAmount,
        couponId = couponId,
        status = OrderStatus.PENDING_PAYMENT,
        items = emptyList(),
    )

    @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외가 발생하고 어떤 서비스도 호출하지 않는다.")
    @Test
    fun throwsBadRequest_whenItemsEmpty() {
        val result = assertThrows<BadRequestException> {
            orderFacade.place(userId = 1L, command = command(items = emptyList(), expectedOriginalAmount = 0))
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(OrderErrorCode.EMPTY_ORDER_ITEMS) },
            { verifyNoInteractions(productService, couponService, inventoryService, orderService) },
        )
    }

    @DisplayName("쿠폰 없이 주문하면, 쿠폰 사용 없이 할인 0원으로 생성하고 재고를 차감한다.")
    @Test
    fun placesOrder_withoutCoupon() {
        val product = product()
        whenever(productService.getActiveProducts(listOf(product.id))).thenReturn(mapOf(product.id to product))
        whenever(orderService.create(eq(1L), any(), any(), eq(Money(0)))).thenReturn(orderInfo())

        val info = orderFacade.place(
            userId = 1L,
            command = command(
                items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                expectedOriginalAmount = 200_000,
            ),
        )

        assertAll(
            { assertThat(info.totalAmount).isEqualTo(200_000) },
            { verifyNoInteractions(couponService) },
            { verify(inventoryService).decreaseStock(listOf(StockDecreaseLine(productId = product.id, quantity = 2))) },
        )
    }

    @DisplayName("쿠폰을 적용하면, 원금액으로 쿠폰을 사용하고 반환된 할인액으로 주문을 생성한다.")
    @Test
    fun placesOrder_withCoupon_passesDiscountToCreate() {
        val product = product()
        whenever(productService.getActiveProducts(listOf(product.id))).thenReturn(mapOf(product.id to product))
        whenever(couponService.use(eq(1L), eq(5L), eq(Money(200_000)), any())).thenReturn(Money(10_000))
        whenever(orderService.create(eq(1L), any(), any(), eq(Money(10_000))))
            .thenReturn(orderInfo(discountAmount = 10_000, couponId = 5L))

        val info = orderFacade.place(
            userId = 1L,
            command = command(
                items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                couponId = 5L,
                expectedOriginalAmount = 200_000,
                expectedDiscountAmount = 10_000,
            ),
        )

        assertAll(
            { assertThat(info.discountAmount).isEqualTo(10_000) },
            { verify(couponService).use(eq(1L), eq(5L), eq(Money(200_000)), any()) },
            { verify(orderService).create(eq(1L), any(), any(), eq(Money(10_000))) },
        )
    }

    @DisplayName("쿠폰 사용 → 주문 생성(금액 게이트) → 재고 차감(비관락) 순서로 호출한다.")
    @Test
    fun orchestratesInOrder_couponThenCreateThenInventory() {
        val product = product()
        whenever(productService.getActiveProducts(listOf(product.id))).thenReturn(mapOf(product.id to product))
        whenever(couponService.use(eq(1L), eq(5L), eq(Money(200_000)), any())).thenReturn(Money(10_000))
        whenever(orderService.create(eq(1L), any(), any(), eq(Money(10_000))))
            .thenReturn(orderInfo(discountAmount = 10_000, couponId = 5L))

        orderFacade.place(
            userId = 1L,
            command = command(
                items = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                couponId = 5L,
                expectedOriginalAmount = 200_000,
                expectedDiscountAmount = 10_000,
            ),
        )

        inOrder(couponService, orderService, inventoryService) {
            verify(couponService).use(eq(1L), eq(5L), eq(Money(200_000)), any())
            verify(orderService).create(eq(1L), any(), any(), eq(Money(10_000)))
            verify(inventoryService).decreaseStock(any())
        }
    }

    @DisplayName("존재하지 않는 상품이 포함되면, NOT_FOUND가 전파되고 쿠폰·재고·주문을 호출하지 않는다.")
    @Test
    fun propagatesNotFound_whenProductMissing() {
        whenever(productService.getActiveProducts(listOf(99L)))
            .thenThrow(NotFoundException(ProductErrorCode.PRODUCT_NOT_FOUND))

        val result = assertThrows<NotFoundException> {
            orderFacade.place(
                userId = 1L,
                command = command(
                    items = listOf(OrderLineCommand(productId = 99L, quantity = 1)),
                    expectedOriginalAmount = 100_000,
                ),
            )
        }

        assertAll(
            { assertThat(result.errorCode).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND) },
            { verifyNoInteractions(couponService) },
            { verify(inventoryService, never()).decreaseStock(any()) },
            { verify(orderService, never()).create(any(), any(), any(), any()) },
        )
    }
}
