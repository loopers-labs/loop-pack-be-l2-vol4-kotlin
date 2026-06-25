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
import java.net.InetSocketAddress
import kotlin.system.measureTimeMillis

class PgSimulatorPaymentGatewayAdapterTest {
    private val pgServer = PgStubServer()

    @AfterEach
    fun tearDown() {
        pgServer.stop()
    }

    @Test
    fun `PG_응답이_튜닝된_read_timeout보다_지연되면_빠르게_불확정_실패로_종료한다`() {
        pgServer.start(delayMillis = 1_200)
        val adapter = adapter()

        val elapsedMillis = measureTimeMillis {
            assertThrows<PaymentGatewayUnknownException> {
                adapter.request(paymentGatewayRequest())
            }
        }

        assertThat(elapsedMillis).isLessThan(1_100)
        assertThat(pgServer.requests).hasSize(1)
    }

    private fun adapter(): PgSimulatorPaymentGatewayAdapter =
        PgSimulatorPaymentGatewayAdapter(
            restTemplateBuilder = RestTemplateBuilder(),
            properties = PgSimulatorPaymentProperties(baseUrl = pgServer.baseUrl),
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
        val requests = mutableListOf<String>()
        val baseUrl: String
            get() = "http://localhost:${server.address.port}"

        fun start(delayMillis: Long = 0) {
            if (started) {
                return
            }
            this.delayMillis = delayMillis
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
            val response = """
                {"meta":{"result":"SUCCESS","errorCode":null,"message":null},"data":{"transactionKey":"20260625:TR:test01","status":"PENDING","reason":null}}
            """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "application/json")
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(HttpStatus.OK.value(), bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }
}
