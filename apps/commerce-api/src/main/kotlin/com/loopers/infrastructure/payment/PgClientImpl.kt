package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCommand
import com.loopers.domain.payment.PgTransactionResult
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.payment.PgUnavailableException
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PgClientImpl(
    private val pgFeignClient: PgFeignClient,
) : PgClient {
    // Resilience4j 기본 aspect 순서: Retry(바깥) → CircuitBreaker(안).
    // fallback 은 최외곽인 Retry 에 둬야, 재시도가 모두 소진된 뒤(또는 서킷 오픈 시) 진입한다.
    @Retry(name = CB_NAME, fallbackMethod = "requestPaymentFallback")
    @CircuitBreaker(name = CB_NAME)
    override fun requestPayment(command: PgPaymentCommand): PgTransactionResult {
        val response = pgFeignClient.requestPayment(
            userId = command.userId,
            request = PgFeignDto.PaymentRequest(
                orderId = command.orderId,
                cardType = command.cardType.name,
                cardNo = command.cardNo,
                amount = command.amount,
                callbackUrl = command.callbackUrl,
            ),
        )
        val data = response.data
            ?: throw PgUnavailableException("PG 응답이 비어있습니다.")
        return PgTransactionResult(
            transactionKey = data.transactionKey,
            orderId = command.orderId,
            status = data.status.toPgStatus(),
            reason = data.reason,
        )
    }

    @Suppress("unused")
    private fun requestPaymentFallback(command: PgPaymentCommand, t: Throwable): PgTransactionResult {
        logger.warn("PG 결제 요청 실패 - fallback 진입. orderId={}, cause={}", command.orderId, t.message)
        throw PgUnavailableException(cause = t)
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getTransactionFallback")
    override fun getTransaction(userId: String, transactionKey: String): PgTransactionResult? {
        val data = pgFeignClient.getTransaction(userId, transactionKey).data ?: return null
        return PgTransactionResult(
            transactionKey = data.transactionKey,
            orderId = data.orderId,
            status = data.status.toPgStatus(),
            reason = data.reason,
        )
    }

    @Suppress("unused")
    private fun getTransactionFallback(userId: String, transactionKey: String, t: Throwable): PgTransactionResult? {
        logger.warn("PG 거래 조회 실패 - fallback 진입. transactionKey={}, cause={}", transactionKey, t.message)
        return null
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "findTransactionsByOrderIdFallback")
    override fun findTransactionsByOrderId(userId: String, orderId: String): List<PgTransactionResult> {
        val data = pgFeignClient.getTransactionsByOrderId(userId, orderId).data ?: return emptyList()
        return data.transactions.map {
            PgTransactionResult(
                transactionKey = it.transactionKey,
                orderId = data.orderId,
                status = it.status.toPgStatus(),
                reason = it.reason,
            )
        }
    }

    @Suppress("unused")
    private fun findTransactionsByOrderIdFallback(
        userId: String,
        orderId: String,
        t: Throwable,
    ): List<PgTransactionResult> {
        logger.warn("PG 주문별 거래 조회 실패 - fallback 진입. orderId={}, cause={}", orderId, t.message)
        return emptyList()
    }

    private fun String.toPgStatus(): PgTransactionStatus = when (this) {
        "SUCCESS" -> PgTransactionStatus.SUCCESS
        "FAILED" -> PgTransactionStatus.FAILED
        else -> PgTransactionStatus.PENDING
    }

    companion object {
        private const val CB_NAME = "pgClient"
        private val logger = LoggerFactory.getLogger(PgClientImpl::class.java)
    }
}
