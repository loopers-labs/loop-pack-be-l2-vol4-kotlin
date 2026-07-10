package com.loopers.domain.order.unit

import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.domain.order.application.OrderFacade
import com.loopers.domain.order.application.service.OrderService
import com.loopers.domain.order.support.OrderSteps.Companion.상품_스냅샷
import com.loopers.domain.order.support.OrderSteps.Companion.주문항목_생성_커맨드
import com.loopers.domain.order.support.OrderSteps.Companion.주문_도메인_생성
import com.loopers.domain.order.support.OrderSteps.Companion.주문_생성_커맨드
import com.loopers.domain.product.application.command.StockDecreaseCommand
import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.product.application.service.StockService
import com.loopers.domain.product.support.ProductSteps.Companion.재고_도메인_생성
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.회원_도메인_생성
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrderFacadeTest {
    @Test
    fun `주문은_유저와_상품을_확인하고_재고를_차감한_뒤_저장한다`() {
        val userService = mockk<UserService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val orderService = mockk<OrderService>()
        val couponService = mockk<CouponService>()
        val orderFacade = OrderFacade(userService, productService, stockService, orderService, couponService)
        val command = 주문_생성_커맨드(
            userId = 1L,
            items = listOf(
                주문항목_생성_커맨드(productId = 10L, quantity = 2),
                주문항목_생성_커맨드(productId = 20L, quantity = 1),
            ),
        )
        every { userService.getById(1L) } returns 회원_도메인_생성(id = 1L)
        every { productService.getOrderableSnapshots(listOf(10L, 20L)) } returns listOf(
            상품_스냅샷(productId = 10L, unitPrice = 10_000),
            상품_스냅샷(productId = 20L, productName = "보조 상품", unitPrice = 5_000),
        )
        every {
            stockService.decreaseAll(
                listOf(
                    StockDecreaseCommand(productId = 10L, quantity = 2),
                    StockDecreaseCommand(productId = 20L, quantity = 1),
                ),
            )
        } returns listOf(
            재고_도메인_생성(productId = 10L, leftStock = 8),
            재고_도메인_생성(productId = 20L, leftStock = 4),
        )
        every { orderService.placeOrder(1L, any(), null) } answers {
            주문_도메인_생성(
                id = 100L,
                orderedUserId = 1L,
                items = secondArg(),
            )
        }

        val info = orderFacade.placeOrder(command)

        assertThat(info.id).isEqualTo(100L)
        assertThat(info.orderedUserId).isEqualTo(1L)
        assertThat(info.paymentPrice).isEqualTo(25_000)
        assertThat(info.items.map { it.productId }).containsExactly(10L, 20L)
        verifySequence {
            userService.getById(1L)
            productService.getOrderableSnapshots(listOf(10L, 20L))
            stockService.decreaseAll(
                listOf(
                    StockDecreaseCommand(productId = 10L, quantity = 2),
                    StockDecreaseCommand(productId = 20L, quantity = 1),
                ),
            )
            orderService.placeOrder(1L, any(), null)
        }
    }

    @Test
    fun `재고가_부족하면_CONFLICT가_발생한다`() {
        val userService = mockk<UserService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val orderService = mockk<OrderService>()
        val couponService = mockk<CouponService>()
        val orderFacade = OrderFacade(userService, productService, stockService, orderService, couponService)
        val command = 주문_생성_커맨드()
        every { userService.getById(1L) } returns 회원_도메인_생성(id = 1L)
        every { productService.getOrderableSnapshots(listOf(10L)) } returns listOf(상품_스냅샷(productId = 10L))
        every {
            stockService.decreaseAll(listOf(StockDecreaseCommand(productId = 10L, quantity = 2)))
        } throws CoreException(ErrorType.CONFLICT)

        val ex = assertThrows<CoreException> {
            orderFacade.placeOrder(command)
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.CONFLICT)
        verify(exactly = 0) { orderService.placeOrder(any(), any(), any()) }
    }

    @Test
    fun `같은_멱등키의_기존_주문이_있으면_기존_주문을_반환한다`() {
        val userService = mockk<UserService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val orderService = mockk<OrderService>()
        val couponService = mockk<CouponService>()
        val orderFacade = OrderFacade(userService, productService, stockService, orderService, couponService)
        val command = 주문_생성_커맨드(idempotencyKey = "order-key-1")
        every { userService.getById(1L) } returns 회원_도메인_생성(id = 1L)
        every {
            orderService.findByOrderedUserIdAndIdempotencyKeyOrNull(1L, "order-key-1")
        } returns 주문_도메인_생성(id = 100L)

        val info = orderFacade.placeOrder(command)

        assertThat(info.id).isEqualTo(100L)
        verify(exactly = 0) { productService.getOrderableSnapshots(any()) }
        verify(exactly = 0) { stockService.decreaseAll(any()) }
        verify(exactly = 0) { orderService.placeOrder(any(), any(), any()) }
    }

    @Test
    fun `사용자가_없으면_주문을_진행하지_않는다`() {
        val userService = mockk<UserService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val orderService = mockk<OrderService>()
        val couponService = mockk<CouponService>()
        val orderFacade = OrderFacade(userService, productService, stockService, orderService, couponService)
        every { userService.getById(1L) } throws CoreException(ErrorType.NOT_FOUND)

        val ex = assertThrows<CoreException> {
            orderFacade.placeOrder(주문_생성_커맨드())
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
        verify(exactly = 0) { productService.getOrderableSnapshots(any()) }
        verify(exactly = 0) { stockService.decreaseAll(any()) }
        verify(exactly = 0) { orderService.placeOrder(any(), any(), any()) }
    }

    @Test
    fun `주문_상품이_존재하지_않으면_주문을_진행하지_않는다`() {
        val userService = mockk<UserService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val orderService = mockk<OrderService>()
        val couponService = mockk<CouponService>()
        val orderFacade = OrderFacade(userService, productService, stockService, orderService, couponService)
        every { userService.getById(1L) } returns 회원_도메인_생성(id = 1L)
        every { productService.getOrderableSnapshots(listOf(10L)) } throws CoreException(ErrorType.NOT_FOUND)

        val ex = assertThrows<CoreException> {
            orderFacade.placeOrder(주문_생성_커맨드())
        }

        assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
        verify(exactly = 0) { stockService.decreaseAll(any()) }
        verify(exactly = 0) { orderService.placeOrder(any(), any(), any()) }
    }
}
