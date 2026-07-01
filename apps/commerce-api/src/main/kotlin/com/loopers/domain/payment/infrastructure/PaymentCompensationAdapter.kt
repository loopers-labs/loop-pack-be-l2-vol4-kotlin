package com.loopers.domain.payment.infrastructure

import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.domain.order.model.OrderModel
import com.loopers.domain.payment.port.PaymentCompensationPort
import com.loopers.domain.product.application.command.StockDecreaseCommand
import com.loopers.domain.product.application.service.StockService
import org.springframework.stereotype.Component

@Component
class PaymentCompensationAdapter(
    private val stockService: StockService,
    private val couponService: CouponService,
) : PaymentCompensationPort {
    override fun restore(order: OrderModel) {
        stockService.restoreAll(
            order.items.map {
                StockDecreaseCommand(
                    productId = it.productId,
                    quantity = it.quantity.value,
                )
            },
        )
        order.issuedCouponId?.let { couponService.cancelUse(it) }
    }
}
