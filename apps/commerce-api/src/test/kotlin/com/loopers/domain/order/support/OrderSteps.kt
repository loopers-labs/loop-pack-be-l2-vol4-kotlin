package com.loopers.domain.order.support

import com.loopers.domain.order.application.command.OrderCreateCommand
import com.loopers.domain.order.application.command.OrderItemCreateCommand
import com.loopers.domain.order.model.OrderItemModel
import com.loopers.domain.order.model.OrderModel
import com.loopers.domain.order.model.OrderStatus
import com.loopers.domain.product.application.info.ProductSnapshotInfo
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.Quantity

class OrderSteps {
    companion object {
        const val 기본_주문_ID: Long = 100L
        const val 기본_주문자_ID: Long = 1L
        const val 기본_상품_ID: Long = 10L
        const val 기본_상품명: String = "기본 상품"
        const val 기본_단가: Long = 10_000L
        const val 기본_수량: Long = 2L

        fun 주문항목_도메인_생성(
            orderId: Long = 0L,
            productId: Long = 기본_상품_ID,
            quantity: Long = 기본_수량,
            productName: String = 기본_상품명,
            unitPrice: Long = 기본_단가,
        ): OrderItemModel = OrderItemModel.snapshotOf(
            orderId = orderId,
            productId = productId,
            quantity = Quantity.of(quantity),
            snapshotProductName = productName,
            snapshotUnitPrice = Money.of(unitPrice),
        )

        fun 주문_도메인_생성(
            id: Long = 기본_주문_ID,
            orderedUserId: Long = 기본_주문자_ID,
            issuedCouponId: Long? = null,
            status: OrderStatus = OrderStatus.PAYMENT_PENDING,
            items: List<OrderItemModel> = listOf(주문항목_도메인_생성(orderId = id)),
        ): OrderModel = OrderModel.create(
            orderedUserId = orderedUserId,
            issuedCouponId = issuedCouponId,
            items = items,
        ).withId(id).copy(status = status)

        fun 주문_생성_커맨드(
            userId: Long = 기본_주문자_ID,
            idempotencyKey: String? = null,
            issuedCouponId: Long? = null,
            items: List<OrderItemCreateCommand> = listOf(주문항목_생성_커맨드()),
        ): OrderCreateCommand = OrderCreateCommand(
            userId = userId,
            idempotencyKey = idempotencyKey,
            issuedCouponId = issuedCouponId,
            items = items,
        )

        fun 주문항목_생성_커맨드(
            productId: Long = 기본_상품_ID,
            quantity: Long = 기본_수량,
        ): OrderItemCreateCommand = OrderItemCreateCommand(
            productId = productId,
            quantity = quantity,
        )

        fun 상품_스냅샷(
            productId: Long = 기본_상품_ID,
            productName: String = 기본_상품명,
            unitPrice: Long = 기본_단가,
        ): ProductSnapshotInfo = ProductSnapshotInfo(
            productId = productId,
            productName = productName,
            unitPrice = unitPrice,
        )
    }
}
