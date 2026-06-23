package com.loopers.application.order

import com.loopers.application.payment.PaymentCancelCommand
import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentGateway
import com.loopers.application.payment.PaymentStatus
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
        val paymentCommand = preparedOrder.toPaymentCommand()
        val paymentResult = paymentGateway.pay(paymentCommand)

        return when (paymentResult.status) {
            PaymentStatus.SUCCESS -> confirmAfterPaymentSuccess(preparedOrder, paymentCommand)
            PaymentStatus.FAILED -> orderReleaseService.markPaymentFailed(preparedOrder.idOrThrow())
            PaymentStatus.PENDING -> preparedOrder
        }.let { OrderInfo.from(it) }
    }

    private fun confirmAfterPaymentSuccess(
        preparedOrder: Order,
        paymentCommand: PaymentCommand,
    ): Order {
        val result = try {
            orderConfirmService.confirm(preparedOrder.idOrThrow())
        } catch (e: RuntimeException) {
            paymentGateway.cancel(paymentCommand.toCancelCommand())
            throw e
        }

        return when (result) {
            is OrderConfirmResult.Confirmed -> result.order
            is OrderConfirmResult.AlreadyPaid -> result.order
            is OrderConfirmResult.AlreadyTerminated -> {
                paymentGateway.cancel(paymentCommand.toCancelCommand())
                throw CoreException(
                    ErrorType.CONFLICT,
                    "주문이 이미 종료되어 결제를 취소했습니다. id=${preparedOrder.idOrThrow()}",
                )
            }
        }
    }

    private fun Order.toPaymentCommand(): PaymentCommand {
        return PaymentCommand(
            orderId = idOrThrow(),
            userId = userId,
            amount = paymentAmount,
            cardType = "",
            cardNo = "",
            callbackUrl = "",
        )
    }

    private fun Order.idOrThrow(): Long {
        return id ?: throw CoreException(ErrorType.INTERNAL_ERROR, "주문 ID가 존재하지 않습니다.")
    }

    private fun PaymentCommand.toCancelCommand(): PaymentCancelCommand {
        return PaymentCancelCommand(
            orderId = orderId,
            userId = userId,
            amount = amount,
        )
    }
}
