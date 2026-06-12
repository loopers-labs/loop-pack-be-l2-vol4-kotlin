package com.loopers.application.order

import com.loopers.application.coupon.CouponApplicationService
import com.loopers.application.product.ProductApplicationService
import com.loopers.application.stock.StockApplicationService
import com.loopers.application.user.UserApplicationService
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderAmountCalculator
import com.loopers.domain.order.OrderAmounts
import com.loopers.domain.order.OrderItem
import com.loopers.domain.order.OrderItemPrice
import com.loopers.domain.order.OrderQuantity
import com.loopers.domain.order.ProductSnapshot
import com.loopers.domain.product.Product
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class OrderPrepareService(
    private val orderApplicationService: OrderApplicationService,
    private val productApplicationService: ProductApplicationService,
    private val stockApplicationService: StockApplicationService,
    private val userApplicationService: UserApplicationService,
    private val couponApplicationService: CouponApplicationService,
) {
    @Transactional
    fun prepare(command: CreateOrderCommand): Order {
        userApplicationService.getUser(command.userId)

        val orderItems = command.items.map { itemCommand ->
            val product = productApplicationService.getProduct(itemCommand.productId)
            val quantity = OrderQuantity(itemCommand.quantity)

            stockApplicationService.deduct(
                productId = product.idOrThrow(),
                amount = quantity.value,
            )
            product.toOrderItem(quantity)
        }

        val amounts = calculateAmounts(command, orderItems)

        command.userCouponId?.let {
            couponApplicationService.useCoupon(userId = command.userId, userCouponId = it)
        }

        return orderApplicationService.createOrder(
            userId = command.userId,
            userCouponId = command.userCouponId,
            items = orderItems,
            amounts = amounts,
        )
    }

    private fun calculateAmounts(command: CreateOrderCommand, orderItems: List<OrderItem>): OrderAmounts {
        val coupon = command.userCouponId
            ?.let { couponApplicationService.getUsableCoupon(userId = command.userId, userCouponId = it) }
        return OrderAmountCalculator.calculate(items = orderItems, coupon = coupon)
    }

    private fun Product.toOrderItem(quantity: OrderQuantity): OrderItem {
        return OrderItem(
            productSnapshot = ProductSnapshot(
                productId = idOrThrow(),
                productName = name,
                productPrice = OrderItemPrice(price.amount),
            ),
            quantity = quantity,
        )
    }

    private fun Product.idOrThrow(): Long {
        return id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "상품 ID가 존재하지 않습니다.")
    }
}
