package com.loopers.application.order

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentCancelCommand
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
        val paymentCommand = preparedOrder.toPaymentCommand()
        val paymentResult = paymentGateway.pay(paymentCommand)

        return when (paymentResult) {
            PaymentResult.SUCCESS -> confirmAfterPaymentSuccess(preparedOrder, paymentCommand)
            PaymentResult.FAILED -> orderReleaseService.markPaymentFailed(preparedOrder.idOrThrow())
        }.let { OrderInfo.from(it) }
    }

    private fun confirmAfterPaymentSuccess(
        preparedOrder: Order,
        paymentCommand: PaymentCommand,
    ): Order {
        val result = try {
            orderConfirmService.confirm(preparedOrder.idOrThrow())
        } catch (e: RuntimeException) {
            // 예기치 못한 실패(주문 조회 불가 등) — 결제는 성공했으므로 결제 취소 후 전파
            paymentGateway.cancel(paymentCommand.toCancelCommand())
            throw e
        }

        return when (result) {
            is OrderConfirmResult.Confirmed -> result.order
            is OrderConfirmResult.AlreadyPaid -> result.order
            is OrderConfirmResult.AlreadyTerminated -> {
                // 결제는 성공했으나 주문이 이미 취소/실패로 종료됨 → 결제 취소(환불) 후 실패로 알림
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
