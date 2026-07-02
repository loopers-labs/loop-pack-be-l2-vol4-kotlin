package com.loopers.payment.infrastructure.pg

import com.loopers.payment.application.PaymentResultStatus
import com.loopers.payment.application.PgPaymentGateway
import com.loopers.payment.application.PgQueryCommand
import com.loopers.payment.application.PgQueryResult
import com.loopers.payment.application.PgSubmitCommand
import com.loopers.payment.application.PgSubmitResult
import feign.FeignException
import feign.RetryableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.ConnectException

@Component
class PgPaymentGatewayImpl(
    private val pgPaymentRequester: PgPaymentRequester,
    private val pgAClient: PgAClient,
    private val pgBClient: PgBClient,
) : PgPaymentGateway {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun submit(command: PgSubmitCommand): PgSubmitResult {
        val userId = command.userId.toString()
        val request = PgPaymentRequest.from(command)

        return when (val primary = attempt { pgPaymentRequester.requestToPgA(userId, request) }) {
            is Attempt.Accepted -> PgSubmitResult.Accepted(primary.transactionKey)
            is Attempt.Rejected -> PgSubmitResult.Rejected(primary.reason)
            is Attempt.InDoubt -> {
                logger.warn("PG-A 결과 미확정({}). UNKNOWN 처리합니다.", primary.message)
                PgSubmitResult.Unknown
            }
            is Attempt.Unavailable -> {
                logger.warn("PG-A 사용 불가({}). PG-B 로 failover 합니다.", primary.message)
                failover(userId, request)
            }
        }
    }

    private fun failover(userId: String, request: PgPaymentRequest): PgSubmitResult =
        when (val secondary = attempt { pgPaymentRequester.requestToPgB(userId, request) }) {
            is Attempt.Accepted -> PgSubmitResult.Accepted(secondary.transactionKey)
            is Attempt.Rejected -> PgSubmitResult.Rejected(secondary.reason)
            is Attempt.InDoubt -> {
                logger.warn("PG-B 결과 미확정({}). UNKNOWN 처리합니다.", secondary.message)
                PgSubmitResult.Unknown
            }
            is Attempt.Unavailable -> {
                logger.warn("PG-B 도 사용 불가({}). 결제 실패 처리합니다.", secondary.message)
                PgSubmitResult.Failed
            }
        }

    private fun attempt(call: () -> PgApiResponse<PgTransactionResponse>): Attempt =
        try {
            val data = call().data
                ?: return Attempt.InDoubt("응답 본문이 비어 있습니다.")
            Attempt.Accepted(data.transactionKey)
        } catch (e: CallNotPermittedException) {
            Attempt.Unavailable(e.message)
        } catch (e: RetryableException) {
            if (e.cause is ConnectException) Attempt.Unavailable(e.message) else Attempt.InDoubt(e.message)
        } catch (e: FeignException.BadRequest) {
            Attempt.Rejected(e.contentUTF8())
        } catch (e: FeignException) {
            Attempt.InDoubt(e.message)
        }

    override fun query(command: PgQueryCommand): PgQueryResult {
        val userId = command.userId.toString()
        val onA = queryOne(pgAClient, userId, command.orderKey)
        if (onA is QueryOne.Found) return onA.result
        val onB = queryOne(pgBClient, userId, command.orderKey)
        if (onB is QueryOne.Found) return onB.result
        return if (onA is QueryOne.Unreachable || onB is QueryOne.Unreachable) {
            PgQueryResult.Unreachable
        } else {
            PgQueryResult.NotFound
        }
    }

    private fun queryOne(client: PgPaymentClient, userId: String, orderKey: String): QueryOne =
        try {
            val transaction = client.findByOrderId(userId, orderKey).data?.transactions?.lastOrNull()
                ?: return QueryOne.Empty
            QueryOne.Found(PgQueryResult.Found(transaction.transactionKey, mapStatus(transaction.status)))
        } catch (e: FeignException) {
            logger.warn("PG 조회 실패({}). orderKey={}", e.message, orderKey)
            QueryOne.Unreachable
        }

    private fun mapStatus(status: String): PaymentResultStatus =
        when (status) {
            "SUCCESS" -> PaymentResultStatus.SUCCESS
            "FAILED" -> PaymentResultStatus.FAILED
            else -> PaymentResultStatus.PENDING
        }

    private sealed interface Attempt {
        data class Accepted(val transactionKey: String) : Attempt
        data class Rejected(val reason: String) : Attempt
        data class Unavailable(val message: String?) : Attempt
        data class InDoubt(val message: String?) : Attempt
    }

    private sealed interface QueryOne {
        data class Found(val result: PgQueryResult.Found) : QueryOne
        data object Empty : QueryOne
        data object Unreachable : QueryOne
    }
}
