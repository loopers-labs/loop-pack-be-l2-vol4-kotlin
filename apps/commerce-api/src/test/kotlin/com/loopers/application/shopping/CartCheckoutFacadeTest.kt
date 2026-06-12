package com.loopers.application.shopping

import com.loopers.application.order.OrderCheckoutFacade
import com.loopers.application.order.OrderInfo
import com.loopers.domain.order.OrderCancelReason
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class CartCheckoutFacadeTest {
    @DisplayName("주문창 접근 성공 시 현재 Catalog 스냅샷으로 checkout 을 호출하고 쇼핑카트를 비운다.")
    @Test
    fun checksOutWithCurrentCatalogSnapshotAndClearsCartAfterSuccess() {
        val fixture = Fixture()
        val expiresAt = LocalDateTime.of(2026, 5, 29, 12, 30)
        fixture.catalogPort.register(
            productId = 100L,
            productName = "현재 상품명",
            brandName = "현재 브랜드명",
            price = 12000L,
            stockQuantity = 10,
        )
        fixture.cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 2))
        whenever(fixture.orderCheckoutFacade.checkout(any())).thenReturn(orderDetail(orderId = 900L, expiresAt = expiresAt))

        val result = fixture.cartCheckoutFacade.checkout(
            CartCommand.Checkout(
                userId = 1L,
                deliveryAddress = "서울시 강남구",
                deliveryRequest = "문 앞",
                phoneNumber = "010-1234-5678",
                reservationExpiresAt = expiresAt,
            ),
        )

        val commandCaptor = argumentCaptor<OrderCommand.Checkout>()
        verify(fixture.orderCheckoutFacade).checkout(commandCaptor.capture())
        val checkoutCommand = commandCaptor.firstValue
        assertAll(
            { assertThat(result.orderId).isEqualTo(900L) },
            { assertThat(fixture.cartApplicationService.getItems(userId = 1L)).isEmpty() },
            { assertThat(checkoutCommand.userId).isEqualTo(1L) },
            { assertThat(checkoutCommand.deliveryAddress).isEqualTo("서울시 강남구") },
            { assertThat(checkoutCommand.deliveryRequest).isEqualTo("문 앞") },
            { assertThat(checkoutCommand.phoneNumber).isEqualTo("010-1234-5678") },
            { assertThat(checkoutCommand.reservationExpiresAt).isEqualTo(expiresAt) },
            { assertThat(checkoutCommand.items).containsExactly(OrderCommand.CheckoutItem(100L, "현재 상품명", "현재 브랜드명", 12000L, 2)) },
        )
    }

    @DisplayName("주문창 접근 실패 시 쇼핑카트를 유지한다.")
    @Test
    fun keepsCartAfterCheckoutFailure() {
        val fixture = Fixture()
        val expiresAt = LocalDateTime.of(2026, 5, 29, 12, 30)
        fixture.catalogPort.register(productId = 100L, stockQuantity = 10)
        fixture.cartFacade.addItem(CartCommand.AddItem(userId = 1L, productId = 100L, quantity = 2))
        doThrow(CoreException(ErrorType.CONFLICT, "재고가 부족합니다."))
            .whenever(fixture.orderCheckoutFacade).checkout(any())

        val exception = assertThrows<CoreException> {
            fixture.cartCheckoutFacade.checkout(
                CartCommand.Checkout(
                    userId = 1L,
                    deliveryAddress = "서울시 강남구",
                    deliveryRequest = "문 앞",
                    phoneNumber = "010-1234-5678",
                    reservationExpiresAt = expiresAt,
                ),
            )
        }

        assertAll(
            { assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT) },
            { assertThat(fixture.cartApplicationService.getItems(userId = 1L)).hasSize(1) },
        )
    }

    private class Fixture {
        val cartRepository = FakeCartRepository()
        val catalogPort = FakeCartCatalogPort()
        val cartApplicationService = CartApplicationService(cartRepository)
        val cartFacade = CartFacade(cartApplicationService, catalogPort)
        val orderCheckoutFacade: OrderCheckoutFacade = mock()
        val cartCheckoutFacade = CartCheckoutFacade(cartApplicationService, catalogPort, orderCheckoutFacade)
    }

    private fun orderDetail(orderId: Long, expiresAt: LocalDateTime): OrderInfo.Detail =
        OrderInfo.Detail(
            orderId = orderId,
            userId = 1L,
            status = OrderStatus.PAYMENT_PENDING,
            reservationExpiresAt = expiresAt,
            cancelReason = null as OrderCancelReason?,
            deliveryAddress = "서울시 강남구",
            deliveryRequest = "문 앞",
            phoneNumber = "010-1234-5678",
            couponId = null,
            totalAmount = 0,
            discountAmount = 0,
            paymentAmount = 0,
            items = emptyList(),
        )
}
