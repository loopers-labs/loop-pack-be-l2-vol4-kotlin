package com.loopers.domain.payment.unit

import com.loopers.domain.payment.infrastructure.pg.PgSimulatorPaymentGatewayAdapter
import com.loopers.domain.payment.infrastructure.pg.PgSimulatorPaymentProperties
import com.loopers.domain.payment.port.PaymentGatewayRequest
import com.loopers.domain.payment.port.PaymentGatewayUnknownException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import java.io.IOException
import java.net.InetSocketAddress
import java.time.Duration
import kotlin.system.measureTimeMillis

class PgSimulatorPaymentGatewayAdapterTest {
    private val pgServer = PgStubServer()

    @AfterEach
    fun tearDown() {
        pgServer.stop()
    }

    @Test
    fun `PG_응답이_튜닝된_read_timeout보다_지연되면_빠르게_불확정_실패로_종료한다`() {
        val pgResponseDelayMillis = 2_500L
        pgServer.start(delayMillis = pgResponseDelayMillis)
        val adapter = adapter(retryMaxAttempts = 1, readTimeout = Duration.ofMillis(200))

        val elapsedMillis = measureTimeMillis {
            assertThrows<PaymentGatewayUnknownException> {
                adapter.request(paymentGatewayRequest())
            }
        }

        assertThat(elapsedMillis).isLessThan(pgResponseDelayMillis)
        assertThat(pgServer.requests).hasSize(1)
    }

    @Test
    fun `PG_요청이_일시적으로_실패하면_총_3회까지_지수_backoff로_재시도한다`() {
        pgServer.start(
            responses = listOf(
                PgResponse(status = HttpStatus.INTERNAL_SERVER_ERROR),
                PgResponse(status = HttpStatus.INTERNAL_SERVER_ERROR),
                PgResponse(status = HttpStatus.OK),
            ),
        )
        val adapter = adapter()

        val result = adapter.request(paymentGatewayRequest())

        assertThat(result.transactionKey).isEqualTo("20260625:TR:test01")
        assertThat(pgServer.requests).hasSize(3)
    }

    @Test
    fun `PG_실패가_누적되어_서킷이_OPEN되면_추가_요청은_PG에_도달하지_않는다`() {
        pgServer.start(responses = listOf(PgResponse(status = HttpStatus.INTERNAL_SERVER_ERROR)))
        val adapter = adapter(
            retryMaxAttempts = 1,
            circuitBreakerSlidingWindowSize = 2,
            circuitBreakerMinimumNumberOfCalls = 2,
        )

        repeat(2) {
            assertThrows<PaymentGatewayUnknownException> {
                adapter.request(paymentGatewayRequest())
            }
        }
        val requestsBeforeOpenCall = pgServer.requests.size
        assertThrows<PaymentGatewayUnknownException> {
            adapter.request(paymentGatewayRequest())
        }

        assertThat(requestsBeforeOpenCall).isEqualTo(2)
        assertThat(pgServer.requests).hasSize(2)
    }

    private fun adapter(
        retryMaxAttempts: Int = 3,
        readTimeout: Duration = Duration.ofMillis(700),
        circuitBreakerSlidingWindowSize: Int = 10,
        circuitBreakerMinimumNumberOfCalls: Int = 5,
    ): PgSimulatorPaymentGatewayAdapter =
        PgSimulatorPaymentGatewayAdapter(
            restTemplateBuilder = RestTemplateBuilder(),
            properties = PgSimulatorPaymentProperties(
                baseUrl = pgServer.baseUrl,
                readTimeout = readTimeout,
                retryMaxAttempts = retryMaxAttempts,
                circuitBreakerSlidingWindowSize = circuitBreakerSlidingWindowSize,
                circuitBreakerMinimumNumberOfCalls = circuitBreakerMinimumNumberOfCalls,
            ),
        )

    private fun paymentGatewayRequest(): PaymentGatewayRequest =
        PaymentGatewayRequest(
            userId = 1L,
            orderId = 10L,
            cardType = "SAMSUNG",
            cardNo = "1234-5678-1234-5678",
            amount = 10_000L,
            callbackUrl = "http://localhost:8080/api/v1/payments/callback",
        )

    private class PgStubServer {
        private val server: HttpServer = HttpServer.create(InetSocketAddress(0), 0)
        private var started: Boolean = false
        private var delayMillis: Long = 0
        private var responses: List<PgResponse> = listOf(PgResponse())
        val requests = mutableListOf<String>()
        val baseUrl: String
            get() = "http://localhost:${server.address.port}"

        fun start(
            delayMillis: Long = 0,
            responses: List<PgResponse> = listOf(PgResponse()),
        ) {
            if (started) {
                return
            }
            this.delayMillis = delayMillis
            this.responses = responses
            server.createContext("/api/v1/payments") { exchange -> handle(exchange) }
            server.start()
            started = true
        }

        fun stop() {
            if (started) {
                server.stop(0)
                started = false
            }
        }

        private fun handle(exchange: HttpExchange) {
            requests.add(exchange.requestBody.bufferedReader().readText())
            if (delayMillis > 0) {
                Thread.sleep(delayMillis)
            }
            val response = responses.getOrElse(requests.lastIndex) { responses.last() }
            val responseBody = """
                {"meta":{"result":"SUCCESS","errorCode":null,"message":null},"data":{"transactionKey":"20260625:TR:test01","status":"PENDING","reason":null}}
            """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "application/json")
            val bytes = responseBody.toByteArray()
            try {
                exchange.sendResponseHeaders(response.status.value(), bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (_: IOException) {
                exchange.close()
            }
        }
    }
}
