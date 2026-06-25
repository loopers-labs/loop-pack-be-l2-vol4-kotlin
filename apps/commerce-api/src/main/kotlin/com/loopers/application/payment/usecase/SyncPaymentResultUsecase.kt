package com.loopers.application.payment.usecase

import com.loopers.application.payment.SyncPaymentResultCommand
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PgStatus
import com.loopers.domain.product.ProductStockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SyncPaymentResultUsecase(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productStockRepository: ProductStockRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    @Transactional
    fun apply(command: SyncPaymentResultCommand) {
        val payment =
            (
                command.transactionKey?.let { paymentRepository.findByTransactionKeyForUpdate(it) }
                    ?: paymentRepository.findByOrderIdForUpdate(command.orderId)
            ) ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다.")

        if (payment.orderId != command.orderId) {
            throw CoreException(ErrorType.BAD_REQUEST, "콜백의 주문 정보가 결제와 일치하지 않습니다.")
        }

        // reconciliation 으로 뒤늦게 transactionKey 를 알게 된 경우 기록
        if (payment.transactionKey == null && command.transactionKey != null) {
            payment.assignTransactionKey(command.transactionKey)
        }

        if (!payment.isPending()) return // 이미 확정됨 → 멱등 no-op

        when (command.status) {
            PgStatus.PENDING -> return // 아직 처리 중 → 다음 기회에
            PgStatus.SUCCESS -> {
                payment.markSuccess()
                order(payment.orderId).markAsPaid()
            }
            PgStatus.FAILED -> {
                val reason = command.failureReason ?: PaymentFailureReason.TIMEOUT_UNKNOWN
                payment.markFailed(reason)
                val order = order(payment.orderId)
                order.markAsFailed()
                compensate(order)
            }
        }
    }

    private fun order(orderId: Long): OrderModel =
        orderRepository.findById(orderId) ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")

    // 결제 실패 보상: 주문 생성 시 차감된 재고 복구 + 사용 쿠폰 원복.
    private fun compensate(order: OrderModel) {
        // ponytail: 주문 생성과 동일한 오름차순 productId 락 순서 — 데드락 방지.
        order.items.sortedBy { it.productId }.forEach { item ->
            val stock = productStockRepository.findByProductIdForUpdate(item.productId)
                ?: throw CoreException(ErrorType.NOT_FOUND, "상품 재고를 찾을 수 없습니다.")
            stock.restore(item.quantity)
        }
        order.userCouponId?.let { userCouponId ->
            userCouponRepository.findByIdAndUserId(userCouponId, order.userId)?.revert()
        }
    }
}
