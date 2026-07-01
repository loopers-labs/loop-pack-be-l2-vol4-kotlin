package com.loopers.application.payment.usecase

import com.loopers.application.payment.SyncPaymentResultCommand
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.PaymentFailureReason
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgStatus
import com.loopers.domain.product.ProductStockRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.RoundingMode
import java.time.ZonedDateTime

@Component
class SyncPaymentResultUsecase(
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val productStockRepository: ProductStockRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun apply(command: SyncPaymentResultCommand) {
        // Non-locking load — CAS (compareAndSetStatus) is the concurrency guard (R2).
        val payment =
            (
                command.transactionKey?.let { paymentRepository.findByTransactionKey(it) }
                    ?: paymentRepository.findByOrderId(command.orderId)
            ) ?: throw CoreException(ErrorType.NOT_FOUND, "결제를 찾을 수 없습니다.")

        // reconciliation 으로 뒤늦게 transactionKey 를 알게 된 경우 기록
        if (payment.transactionKey == null && command.transactionKey != null) {
            payment.assignTransactionKey(command.transactionKey)
            paymentRepository.save(payment)
        }

        // R6: orderId/amount 검증
        if (payment.orderId != command.orderId) {
            throw CoreException(ErrorType.BAD_REQUEST, "콜백의 주문 정보가 결제와 일치하지 않습니다.")
        }
        if (command.amount != null && command.amount != payment.amount.setScale(0, RoundingMode.DOWN).toLong()) {
            throw CoreException(ErrorType.BAD_REQUEST, "결제 금액이 일치하지 않습니다.")
        }

        if (command.status == PgStatus.PENDING) return // 아직 처리 중 → 다음 기회에

        // Load order to check CANCELLED before CAS
        val orderStatus = orderRepository.findById(payment.orderId)?.status
            ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")

        val now = ZonedDateTime.now()

        when {
            command.status == PgStatus.SUCCESS &&
                (orderStatus == OrderStatus.CANCELLED || orderStatus == OrderStatus.FAILED) -> {
                // R6: 결제 성공이지만 주문이 이미 취소/실패됨 → REFUND_REQUIRED 격리 (주문 전이 없음).
                // FAILED 를 함께 처리하지 않으면 markAsPaid() 상태 전이 위반으로 롤백되어
                // 결제가 PENDING 으로 남아 배치가 무한 재시도하는 Stuck 상태가 된다.
                val affected = paymentRepository.compareAndSetStatus(payment.id, PaymentStatus.REFUND_REQUIRED, null, now)
                if (affected == 1) {
                    log.warn(
                        "Payment {} succeeded but order {} is {} — marked REFUND_REQUIRED, manual refund required",
                        payment.id,
                        payment.orderId,
                        orderStatus,
                    )
                }
            }
            command.status == PgStatus.SUCCESS -> {
                val affected = paymentRepository.compareAndSetStatus(payment.id, PaymentStatus.SUCCESS, null, now)
                if (affected == 1) {
                    // Re-load order after clearAutomatically clears persistence context
                    val order = orderRepository.findById(payment.orderId)
                        ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")
                    order.markAsPaid()
                }
            }
            else -> { // FAILED
                val reason = command.failureReason ?: PaymentFailureReason.TIMEOUT_UNKNOWN
                val affected = paymentRepository.compareAndSetStatus(payment.id, PaymentStatus.FAILED, reason, now)
                if (affected == 1) {
                    // Re-load order after clearAutomatically clears persistence context
                    val order = orderRepository.findById(payment.orderId)
                        ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")
                    if (order.status == OrderStatus.PENDING) {
                        order.markAsFailed()
                        compensate(order)
                    } else {
                        // ponytail: mirrors REFUND_REQUIRED isolation — cancel flow owns its own restoration
                        log.warn(
                            "Payment {} failed but order {} is {} — payment marked FAILED, order left unchanged",
                            payment.id,
                            payment.orderId,
                            order.status,
                        )
                    }
                }
            }
        }
    }

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
