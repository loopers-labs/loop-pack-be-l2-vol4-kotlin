package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentGatewayPort
import com.loopers.domain.payment.PaymentGatewayRequest
import com.loopers.domain.payment.PaymentGatewayResponse
import com.loopers.domain.payment.PaymentStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * PG 연동 어댑터.
 *
 * 외부 PG 호출의 회복 전략(타임아웃·재시도·서킷 브레이커·폴백)은 이 인프라 어댑터 내부에 숨긴다.
 * 도메인 포트([PaymentGatewayPort])와 상위 계층은 회복 전략의 존재를 알지 못한다.
 *
 * - 타임아웃: [PgFeignConfig] 의 connect/read timeout
 * - 재시도: 일시적 장애(타임아웃·IO 오류)에 한해 백오프 후 재시도 (`pgPayment` retry 인스턴스)
 * - 서킷 브레이커: 반복 실패 시 회로를 열어 호출 자체를 차단 (`pgPayment` circuitbreaker 인스턴스)
 * - 폴백: 재시도 소진/회로 open 시 [requestPaymentFallback] 로 장애를 격리해 상위로 전파를 차단
 *
 * resilience4j 기본 aspect 순서상 Retry 가 CircuitBreaker 를 감싸므로,
 * 각 재시도 호출이 서킷 브레이커를 통과한다.
 */
@Component
class PgPaymentGatewayAdapter(
    private val pgPaymentClient: PgPaymentClient,
    @Value("\${pg-simulator.callback-url}") private val callbackUrl: String,
) : PaymentGatewayPort {

    private val log = LoggerFactory.getLogger(javaClass)

    // 폴백은 최외곽 @Retry 에만 둔다. inner @CircuitBreaker 에 폴백을 두면
    // 첫 실패가 곧바로 폴백 처리되어 재시도가 동작하지 못한다.
    @Retry(name = PG_RESILIENCE_INSTANCE, fallbackMethod = "requestPaymentFallback")
    @CircuitBreaker(name = PG_RESILIENCE_INSTANCE)
    override fun requestPayment(request: PaymentGatewayRequest): PaymentGatewayResponse {
        val response = pgPaymentClient.requestPayment(
            userId = request.userId.toString(),
            request = PgPaymentRequest(
                orderId = toPgOrderId(request.orderId),
                cardType = CardType.from(request.cardType).name,
                cardNo = request.cardNo,
                amount = request.amount,
                callbackUrl = callbackUrl,
            ),
        )
        val data = response.data
            ?: throw CoreException(ErrorType.INTERNAL_ERROR, "PG 응답이 올바르지 않습니다.")
        return PaymentGatewayResponse(
            transactionKey = data.transactionKey,
            status = PaymentStatus.valueOf(data.status.uppercase()),
            reason = data.reason,
        )
    }

    /**
     * PG 거래 상태 조회(상태 복구용). 콜백이 유실됐거나 응답 타임아웃으로 결과가 미확정인
     * 결제건을 PG 에 직접 되물어 확정한다. 조회는 멱등·안전하므로 회복 전략을 두지 않고,
     * 실패하면 다음 복구 시도(재호출)에 맡긴다.
     */
    override fun getTransaction(userId: Long, transactionKey: String): PaymentGatewayResponse? {
        val response = pgPaymentClient.getTransaction(userId.toString(), transactionKey)
        val data = response.data ?: return null
        return PaymentGatewayResponse(
            transactionKey = data.transactionKey,
            status = PaymentStatus.valueOf(data.status.uppercase()),
            reason = data.reason,
        )
    }

    /**
     * 재시도가 모두 실패했거나 서킷이 open 되어 호출이 차단됐을 때의 폴백.
     * PG 응답 자체가 비정상인 [CoreException] 은 본래 의미를 보존해 그대로 전파하고,
     * 그 외 인프라 장애(타임아웃·IO·서킷 차단)는 [ErrorType.SERVICE_UNAVAILABLE] 로 격리한다.
     */
    @Suppress("unused")
    private fun requestPaymentFallback(request: PaymentGatewayRequest, t: Throwable): PaymentGatewayResponse {
        if (t is CoreException) {
            throw t
        }
        log.error("PG 결제 요청 실패로 폴백 처리합니다. orderId={}", request.orderId, t)
        throw CoreException(ErrorType.SERVICE_UNAVAILABLE, "결제 시스템이 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.")
    }

    companion object {
        private const val PG_RESILIENCE_INSTANCE = "pgPayment"
        private const val PG_ORDER_ID_MIN_LENGTH = 6

        /** PG 시뮬레이터는 주문 ID가 6자리 이상 문자열이어야 한다. 짧으면 앞을 0으로 채운다. */
        internal fun toPgOrderId(orderId: Long): String =
            orderId.toString().padStart(PG_ORDER_ID_MIN_LENGTH, '0')
    }
}
