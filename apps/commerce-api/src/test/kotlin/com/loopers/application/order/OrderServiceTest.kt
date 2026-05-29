package com.loopers.application.order

import com.loopers.domain.inventory.Inventory
import com.loopers.domain.inventory.InventoryErrorCode
import com.loopers.domain.inventory.InventoryRepository
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorCode
import com.loopers.domain.order.OrderItemSnapshot
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorCode
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.shared.Money
import com.loopers.support.error.BadRequestException
import com.loopers.support.error.ConflictException
import com.loopers.support.error.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OrderServiceTest {
    private val orderRepository: OrderRepository = mock()
    private val productRepository: ProductRepository = mock()
    private val inventoryRepository: InventoryRepository = mock()
    private val orderService = OrderService(orderRepository, productRepository, inventoryRepository)

    private fun product() = Product(brandId = 10L, name = ProductName("에어맥스"), price = Money(100_000))

    private fun ownedOrder(userId: Long = 1L) =
        Order.create(userId, listOf(OrderItemSnapshot(1L, 10L, "에어맥스", "나이키", Money(1000), 1)))

    @DisplayName("주문을 생성하면, 재고를 차감하고 스냅샷으로 주문을 저장한 뒤 정보를 반환한다.")
    @Test
    fun placesOrder_decreasesInventory_andSaves() {
        val product = product()
        val inventory = Inventory.createFor(product.id, 100)
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id))).thenReturn(listOf(product))
        whenever(inventoryRepository.findAllByProductIdIn(listOf(product.id))).thenReturn(listOf(inventory))
        whenever(orderRepository.save(any())).thenAnswer { it.arguments[0] as Order }

        val info = orderService.place(
            userId = 1L,
            command = OrderCreateCommand(items = listOf(OrderLineCommand(productId = product.id, quantity = 2))),
        )

        assertAll(
            { assertThat(inventory.quantity).isEqualTo(98) },
            { assertThat(info.totalAmount).isEqualTo(200_000) },
            { assertThat(info.items).hasSize(1) },
            { assertThat(info.items.first().productName).isEqualTo("에어맥스") },
            { assertThat(info.userId).isEqualTo(1L) },
        )
        verify(orderRepository).save(any())
    }

    @DisplayName("주문 항목이 비어 있으면, BAD_REQUEST 예외가 발생한다.")
    @Test
    fun throwsBadRequest_whenItemsEmpty() {
        val result = assertThrows<BadRequestException> {
            orderService.place(userId = 1L, command = OrderCreateCommand(items = emptyList()))
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
                command = OrderCreateCommand(items = listOf(OrderLineCommand(productId = 99L, quantity = 1))),
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
        whenever(inventoryRepository.findAllByProductIdIn(listOf(product.id))).thenReturn(listOf(inventory))

        val result = assertThrows<ConflictException> {
            orderService.place(
                userId = 1L,
                command = OrderCreateCommand(items = listOf(OrderLineCommand(productId = product.id, quantity = 5))),
            )
        }

        assertThat(result.errorCode).isEqualTo(InventoryErrorCode.STOCK_INSUFFICIENT)
    }

    @DisplayName("상품 재고 행이 없으면, NOT_FOUND(재고) 예외가 발생한다.")
    @Test
    fun throwsNotFound_whenInventoryMissing() {
        val product = product()
        whenever(productRepository.findAllActiveByIdIn(listOf(product.id))).thenReturn(listOf(product))
        whenever(inventoryRepository.findAllByProductIdIn(listOf(product.id))).thenReturn(emptyList())

        val result = assertThrows<NotFoundException> {
            orderService.place(
                userId = 1L,
                command = OrderCreateCommand(items = listOf(OrderLineCommand(productId = product.id, quantity = 1))),
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
