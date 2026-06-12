package com.loopers.application.order

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentResult
import com.loopers.domain.order.Order
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class OrderFacade(
    private val orderPrepareService: OrderPrepareService,
    private val orderConfirmService: OrderConfirmService,
    private val orderReleaseService: OrderReleaseService,
    private val paymentGateway: PaymentGateway,
) {
    fun placeOrder(command: CreateOrderCommand): OrderInfo {
        val preparedOrder = orderPrepareService.prepare(command)
        val paymentResult = paymentGateway.pay(preparedOrder.toPaymentCommand())

        return when (paymentResult) {
            PaymentResult.SUCCESS -> orderConfirmService.confirm(preparedOrder.idOrThrow())
            PaymentResult.FAILED -> orderReleaseService.markPaymentFailed(preparedOrder.idOrThrow())
        }.let { OrderInfo.from(it) }
    }

    private fun Order.toPaymentCommand(): PaymentCommand {
        return PaymentCommand(
            orderId = idOrThrow(),
            userId = userId,
            amount = paymentAmount,
        )
    }

    private fun Order.idOrThrow(): Long {
        return id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "주문 ID가 존재하지 않습니다.")
    }
}
