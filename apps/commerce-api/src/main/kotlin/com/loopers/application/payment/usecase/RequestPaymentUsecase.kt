package com.loopers.application.payment.usecase

import com.loopers.application.payment.PaymentInfo
import com.loopers.application.payment.RequestPaymentCommand
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.PaymentModel
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgRequestCommand
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import java.math.RoundingMode
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class RequestPaymentUsecase(
    private val userService: UserService,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val pgClient: PgClient,
    private val transactionTemplate: TransactionTemplate,
    @Value("\${pg.callback-url}") private val callbackUrl: String,
) {
    fun request(command: RequestPaymentCommand): PaymentInfo {
        val user = userService.getProfile(command.loginId, command.password)

        // (1) 주문 검증 + Payment(PENDING) 저장 — 트랜잭션 안. 커밋 후 외부 호출.
        val payment = try {
            transactionTemplate.execute {
                val order = orderRepository.findById(command.orderId)
                    ?: throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")
                if (order.userId != user.id) throw CoreException(ErrorType.NOT_FOUND, "주문을 찾을 수 없습니다.")
                if (!order.isPending()) throw CoreException(ErrorType.CONFLICT, "결제할 수 없는 주문 상태입니다.")
                if (paymentRepository.findByOrderId(order.id) != null) {
                    throw CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중인 주문입니다.")
                }
                paymentRepository.save(
                    PaymentModel(
                        orderId = order.id,
                        userId = user.id,
                        amount = order.paidPrice,
                        cardType = command.cardType,
                        cardNo = command.cardNo,
                    ),
                ) to order
            }
        } catch (e: DataIntegrityViolationException) {
            // DB unique constraint on order_id — concurrent duplicate insert blocked at commit
            throw CoreException(ErrorType.CONFLICT, "이미 결제가 진행 중인 주문입니다.")
        } ?: throw CoreException(ErrorType.INTERNAL_ERROR, "결제 생성 트랜잭션이 비정상 종료되었습니다.")
        val (savedPayment, order) = payment

        // (2) 트랜잭션 밖에서 PG 호출. 서킷/타임아웃은 어댑터에서 흡수(PENDING 반환).
        val result = pgClient.requestPayment(
            PgRequestCommand(
                orderId = order.id,
                userId = user.id,
                // ponytail: 소수점 발생은 비율 쿠폰 적용 시 드문 케이스. KRW 정수 정책상 내림(초과 청구 방지).
                amount = order.paidPrice.setScale(0, RoundingMode.DOWN).toLong(),
                cardType = command.cardType,
                cardNo = command.cardNo,
                callbackUrl = callbackUrl,
            ),
        )

        // (3) 접수 성공 시 transactionKey 짧은 트랜잭션으로 기록. 미수신이면 PENDING 유지(reconciliation).
        val txKey = result.transactionKey
            ?: return PaymentInfo.from(savedPayment)

        val recorded = transactionTemplate.execute {
            val reloaded = paymentRepository.findByOrderIdForUpdate(order.id)
                ?: return@execute null
            if (reloaded.transactionKey == null) reloaded.markAccepted(txKey, ZonedDateTime.now())
            reloaded
        } ?: throw CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다.")
        return PaymentInfo.from(recorded)
    }
}
