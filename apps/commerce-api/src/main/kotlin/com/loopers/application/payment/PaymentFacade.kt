package com.loopers.application.payment

import com.loopers.domain.order.OrderService
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentService
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCommand
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.payment.PgUnavailableException
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PaymentFacade(
    private val userService: UserService,
    private val orderService: OrderService,
    private val paymentService: PaymentService,
    private val pgClient: PgClient,
    @Value("\${pg.callback-url}") private val callbackUrl: String,
) {
    /**
     * 주문에 대한 카드 결제를 요청한다.
     *
     * 트랜잭션 경계: PG 외부 호출은 DB 트랜잭션 밖에서 수행한다.
     * - prepare(): 결제건을 PENDING 으로 영속 (짧은 TX)
     * - PG 요청: 외부 호출 (Timeout/CircuitBreaker/Retry/Fallback 적용)
     * - assignTransactionKey(): 거래 키 반영 (짧은 TX)
     *
     * PG 가 비동기 처리이므로 요청 시점 결과는 항상 PENDING 이며, 최종 결과는 콜백/동기화로 확정한다.
     */
    fun pay(loginId: String, rawPassword: String, orderId: Long, cardType: CardType, cardNo: String): PaymentInfo {
        val user = userService.authenticate(loginId, rawPassword)
        val order = orderService.getByIdAndUserId(orderId, user.id)
        if (order.status != OrderStatus.PENDING) {
            throw CoreException(ErrorType.CONFLICT, "결제할 수 없는 주문 상태입니다: ${order.status}")
        }

        paymentService.prepare(
            orderId = order.id,
            userId = user.id,
            cardType = cardType,
            cardNo = cardNo,
            amount = order.finalAmount,
        )

        try {
            val result = pgClient.requestPayment(
                PgPaymentCommand(
                    userId = user.id.toString(),
                    orderId = toPgOrderId(order.id),
                    cardType = cardType,
                    cardNo = cardNo,
                    amount = order.finalAmount,
                    callbackUrl = callbackUrl,
                ),
            )
            paymentService.assignTransactionKey(order.id, result.transactionKey)
        } catch (e: PgUnavailableException) {
            // 접수 자체 실패 → 결제는 PENDING 으로 남기고 "결제 대기" 로 응답. 동기화로 추후 복구한다.
            logger.warn("PG 결제 접수 실패 - 결제 대기 처리. orderId={}, cause={}", order.id, e.message)
        }

        return PaymentInfo.from(paymentService.getByOrderId(order.id))
    }

    /**
     * PG 콜백을 수신해 결제 결과를 확정한다. 중복 콜백에도 멱등하게 동작한다.
     */
    fun handleCallback(transactionKey: String, status: PgTransactionStatus, reason: String?) {
        when (status) {
            PgTransactionStatus.SUCCESS -> {
                val payment = paymentService.markSuccess(transactionKey)
                orderService.payIfPending(payment.orderId)
            }
            PgTransactionStatus.FAILED -> paymentService.markFailed(transactionKey, reason)
            PgTransactionStatus.PENDING -> {
                // 아직 확정 전 상태의 콜백은 무시한다. (최종 결과 콜백만 반영)
            }
        }
    }

    /**
     * 사용자 요청에 의한 수동 동기화. 소유권을 검증한 뒤 상태를 복구하고 최신 결제 정보를 반환한다.
     */
    fun syncByUser(loginId: String, rawPassword: String, orderId: Long): PaymentInfo {
        val user = userService.authenticate(loginId, rawPassword)
        orderService.getByIdAndUserId(orderId, user.id)
        sync(orderId)
        return PaymentInfo.from(paymentService.getByOrderId(orderId))
    }

    /**
     * 콜백 유실/타임아웃에 대비한 상태 동기화 복구.
     * PENDING 결제건을 PG 에 직접 조회해 최종 상태를 반영한다.
     * 거래 키가 없으면(요청 타임아웃 등) 주문 ID 로 거래를 역조회해 키를 회수한다.
     */
    fun sync(orderId: Long) {
        val payment = paymentService.getByOrderId(orderId)
        if (payment.status != PaymentStatus.PENDING) return

        val userId = payment.userId.toString()
        val pgResult = payment.transactionKey?.let { key ->
            pgClient.getTransaction(userId, key)
        } ?: pgClient.findTransactionsByOrderId(userId, toPgOrderId(orderId)).firstOrNull()
            ?.also { paymentService.assignTransactionKey(orderId, it.transactionKey) }

        when (pgResult?.status) {
            PgTransactionStatus.SUCCESS -> {
                paymentService.markSuccess(pgResult.transactionKey)
                orderService.payIfPending(orderId)
            }
            PgTransactionStatus.FAILED -> paymentService.markFailed(pgResult.transactionKey, pgResult.reason)
            else -> {
                // PENDING 또는 조회 실패(null) → 아직 처리 중. 다음 동기화 주기에 재시도한다.
                logger.info("결제 동기화 보류 - 아직 처리 중. orderId={}", orderId)
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PaymentFacade::class.java)

        /** pg-simulator 는 orderId 가 6자 이상을 요구하므로 최소 길이를 보정한다. */
        private fun toPgOrderId(orderId: Long): String = orderId.toString().padStart(6, '0')
    }
}
