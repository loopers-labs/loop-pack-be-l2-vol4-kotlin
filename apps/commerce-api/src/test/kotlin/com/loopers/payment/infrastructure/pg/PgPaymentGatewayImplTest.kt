package com.loopers.payment.infrastructure.pg

import com.loopers.payment.application.PaymentResultStatus
import com.loopers.payment.application.PgQueryCommand
import com.loopers.payment.application.PgQueryResult
import com.loopers.payment.application.PgSubmitCommand
import com.loopers.payment.application.PgSubmitResult
import com.loopers.payment.domain.CardType
import feign.FeignException
import feign.Request
import feign.Response
import feign.RetryableException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

class PgPaymentGatewayImplTest {
    private val pgPaymentRequester: PgPaymentRequester = mock()
    private val pgAClient: PgAClient = mock()
    private val pgBClient: PgBClient = mock()
    private val gateway = PgPaymentGatewayImpl(pgPaymentRequester, pgAClient, pgBClient)

    private fun command() = PgSubmitCommand(
        userId = 1L,
        orderKey = "order-123456",
        cardType = CardType.SAMSUNG,
        cardNo = "1234-1234-1234-1234",
        amount = 1_000,
        callbackUrl = "http://localhost:8080/api/v1/payments/callback",
    )

    private fun transaction(key: String, status: String) =
        PgApiResponse(PgMeta("SUCCESS", null, null), PgTransactionResponse(key, status, null))

    private fun emptyBody() =
        PgApiResponse<PgTransactionResponse>(PgMeta("SUCCESS", null, null), null)

    private fun orderResponse(transactions: List<PgTransactionResponse>) =
        PgApiResponse(PgMeta("SUCCESS", null, null), PgOrderResponse("order-123456", transactions))

    private fun request() =
        Request.create(Request.HttpMethod.POST, "http://localhost:8082", emptyMap(), ByteArray(0), StandardCharsets.UTF_8, null)

    private fun retryable(cause: Throwable) =
        RetryableException(503, cause.message, Request.HttpMethod.POST, cause, 0L, request())

    private fun feignStatus(status: Int, body: String): FeignException {
        val response = Response.builder()
            .status(status)
            .reason("error")
            .request(request())
            .headers(emptyMap())
            .body(body, StandardCharsets.UTF_8)
            .build()
        return FeignException.errorStatus("PgClient#request", response)
    }

    private fun callNotPermitted(): CallNotPermittedException {
        val circuitBreaker = CircuitBreaker.ofDefaults("pg-test")
        circuitBreaker.transitionToOpenState()
        return CallNotPermittedException.createCallNotPermittedException(circuitBreaker)
    }

    @DisplayName("PG-A 가 정상 응답하면 Accepted 를 반환하고 PG-B 는 호출하지 않는다.")
    @Test
    fun accepted_whenPgASucceeds() {
        whenever(pgPaymentRequester.requestToPgA(any(), any())).thenReturn(transaction("tx-a", "PENDING"))

        val result = gateway.submit(command())

        assertAll(
            { assertThat(result).isEqualTo(PgSubmitResult.Accepted("tx-a")) },
            { verify(pgPaymentRequester, never()).requestToPgB(any(), any()) },
        )
    }

    @DisplayName("PG-A 가 400(BadRequest)이면 Rejected(본문)을 반환한다.")
    @Test
    fun rejected_whenPgABadRequest() {
        whenever(pgPaymentRequester.requestToPgA(any(), any())).thenThrow(feignStatus(400, "카드 한도 초과"))

        val result = gateway.submit(command())

        assertThat(result).isEqualTo(PgSubmitResult.Rejected("카드 한도 초과"))
    }

    @DisplayName("PG-A 가 500(전송 후 서버오류)이면 UNKNOWN 이고 PG-B 로 failover 하지 않는다.")
    @Test
    fun unknown_whenPgAServerError() {
        whenever(pgPaymentRequester.requestToPgA(any(), any())).thenThrow(feignStatus(500, "server"))

        val result = gateway.submit(command())

        assertAll(
            { assertThat(result).isEqualTo(PgSubmitResult.Unknown) },
            { verify(pgPaymentRequester, never()).requestToPgB(any(), any()) },
        )
    }

    @DisplayName("PG-A 가 read timeout(전송됐을 수 있음)이면 UNKNOWN 이고 failover 하지 않는다.")
    @Test
    fun unknown_whenPgAReadTimeout() {
        whenever(pgPaymentRequester.requestToPgA(any(), any()))
            .thenThrow(retryable(SocketTimeoutException("read timed out")))

        val result = gateway.submit(command())

        assertAll(
            { assertThat(result).isEqualTo(PgSubmitResult.Unknown) },
            { verify(pgPaymentRequester, never()).requestToPgB(any(), any()) },
        )
    }

    @DisplayName("PG-A 응답 본문이 비면 UNKNOWN 이다.")
    @Test
    fun unknown_whenPgAEmptyBody() {
        whenever(pgPaymentRequester.requestToPgA(any(), any())).thenReturn(emptyBody())

        val result = gateway.submit(command())

        assertThat(result).isEqualTo(PgSubmitResult.Unknown)
    }

    @DisplayName("PG-A 가 connection refused(확실히 미전송)이면 PG-B 로 failover 한다.")
    @Test
    fun failover_whenPgAConnectionRefused() {
        whenever(pgPaymentRequester.requestToPgA(any(), any()))
            .thenThrow(retryable(ConnectException("connection refused")))
        whenever(pgPaymentRequester.requestToPgB(any(), any())).thenReturn(transaction("tx-b", "PENDING"))

        val result = gateway.submit(command())

        assertThat(result).isEqualTo(PgSubmitResult.Accepted("tx-b"))
    }

    @DisplayName("PG-A 의 회로가 열려(CallNotPermitted) 있으면 PG-B 로 failover 한다.")
    @Test
    fun failover_whenPgACircuitOpen() {
        whenever(pgPaymentRequester.requestToPgA(any(), any())).thenThrow(callNotPermitted())
        whenever(pgPaymentRequester.requestToPgB(any(), any())).thenReturn(transaction("tx-b", "PENDING"))

        val result = gateway.submit(command())

        assertThat(result).isEqualTo(PgSubmitResult.Accepted("tx-b"))
    }

    @DisplayName("PG-A·PG-B 모두 사용 불가면 Failed 를 반환한다.")
    @Test
    fun failed_whenBothUnavailable() {
        whenever(pgPaymentRequester.requestToPgA(any(), any())).thenThrow(callNotPermitted())
        whenever(pgPaymentRequester.requestToPgB(any(), any())).thenThrow(callNotPermitted())

        val result = gateway.submit(command())

        assertThat(result).isEqualTo(PgSubmitResult.Failed)
    }

    @DisplayName("PG-A 미전송으로 넘어간 PG-B 가 in-doubt 이면 UNKNOWN 이다.")
    @Test
    fun unknown_whenFailoverPgBInDoubt() {
        whenever(pgPaymentRequester.requestToPgA(any(), any()))
            .thenThrow(retryable(ConnectException("connection refused")))
        whenever(pgPaymentRequester.requestToPgB(any(), any()))
            .thenThrow(retryable(SocketTimeoutException("read timed out")))

        val result = gateway.submit(command())

        assertThat(result).isEqualTo(PgSubmitResult.Unknown)
    }

    @DisplayName("query: PG-A 에서 거래를 찾으면 Found 를 반환하고 PG-B 는 조회하지 않는다.")
    @Test
    fun query_foundOnPgA() {
        whenever(pgAClient.findByOrderId(any(), any())).thenReturn(orderResponse(listOf(PgTransactionResponse("tx-a", "SUCCESS", null))))

        val result = gateway.query(PgQueryCommand(1L, "order-123456"))

        assertAll(
            { assertThat(result).isEqualTo(PgQueryResult.Found("tx-a", PaymentResultStatus.SUCCESS)) },
            { verify(pgBClient, never()).findByOrderId(any(), any()) },
        )
    }

    @DisplayName("query: PG-A 가 비어 있고 PG-B 에 있으면 PG-B 결과(Found)를 반환한다.")
    @Test
    fun query_foundOnPgB() {
        whenever(pgAClient.findByOrderId(any(), any())).thenReturn(orderResponse(emptyList()))
        whenever(pgBClient.findByOrderId(any(), any())).thenReturn(orderResponse(listOf(PgTransactionResponse("tx-b", "FAILED", "한도초과"))))

        val result = gateway.query(PgQueryCommand(1L, "order-123456"))

        assertThat(result).isEqualTo(PgQueryResult.Found("tx-b", PaymentResultStatus.FAILED))
    }

    @DisplayName("query: 양쪽 모두 거래가 없으면 NotFound 를 반환한다.")
    @Test
    fun query_notFoundOnBoth() {
        whenever(pgAClient.findByOrderId(any(), any())).thenReturn(orderResponse(emptyList()))
        whenever(pgBClient.findByOrderId(any(), any())).thenReturn(orderResponse(emptyList()))

        val result = gateway.query(PgQueryCommand(1L, "order-123456"))

        assertThat(result).isEqualTo(PgQueryResult.NotFound)
    }

    @DisplayName("query: 한쪽이라도 조회 불가(장애)면 Unreachable 을 반환한다.")
    @Test
    fun query_unreachable_whenAnyClientFails() {
        whenever(pgAClient.findByOrderId(any(), any())).thenThrow(feignStatus(500, "down"))
        whenever(pgBClient.findByOrderId(any(), any())).thenReturn(orderResponse(emptyList()))

        val result = gateway.query(PgQueryCommand(1L, "order-123456"))

        assertThat(result).isEqualTo(PgQueryResult.Unreachable)
    }
}
