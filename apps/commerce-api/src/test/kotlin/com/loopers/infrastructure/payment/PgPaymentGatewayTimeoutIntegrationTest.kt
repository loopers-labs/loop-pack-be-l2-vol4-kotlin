package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentGatewayPort
import com.loopers.domain.payment.PaymentGatewayRequest
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.concurrent.TimeUnit

/**
 * PG 어댑터의 회복 전략을 "실제 네트워크 타임아웃"으로 검증한다.
 * MockWebServer 가 readTimeout(3s)보다 긴 지연 응답을 주어 실제 [java.net.SocketTimeoutException] 을 유발하고,
 * 재시도 소진 → 폴백(SERVICE_UNAVAILABLE) 까지 실제 HTTP 스택(Feign + PgFeignConfig 타임아웃)을 통과시킨다.
 * (예외를 직접 던지는 목 테스트와 달리, 타임아웃 설정 자체가 동작함을 입증한다.)
 */
@SpringBootTest
class PgPaymentGatewayTimeoutIntegrationTest @Autowired constructor(
    private val paymentGatewayPort: PaymentGatewayPort,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
) {
    @BeforeEach
    fun resetCircuit() {
        circuitBreakerRegistry.circuitBreaker("pgPayment").reset()
    }

    private val request = PaymentGatewayRequest(
        userId = 1L,
        orderId = 1L,
        cardType = "SAMSUNG",
        cardNo = "1234-5678-9814-1451",
        amount = 5_000L,
    )

    @DisplayName("PG 응답이 readTimeout 을 초과하면 실제 타임아웃이 발생하고, 재시도 소진 후 폴백으로 503을 던진다.")
    @Test
    fun realReadTimeout_retriesThenFallsBack() {
        // readTimeout(5s) 보다 확실히 긴 지연 → 실제 SocketTimeoutException 유발
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse().setHeadersDelay(8, TimeUnit.SECONDS).setResponseCode(200)
        }

        val ex = assertThrows<CoreException> { paymentGatewayPort.requestPayment(request) }

        // 일시적 장애(타임아웃)는 인프라 장애로 격리되어 503 으로 폴백된다.
        assertThat(ex.errorType).isEqualTo(ErrorType.SERVICE_UNAVAILABLE)
        // 화이트리스트(IOException 하위)에 매칭되어 실제 PG 호출이 최대 3회까지 재시도된다.
        assertThat(server.requestCount).isEqualTo(3)
    }

    companion object {
        private val server = MockWebServer()

        @JvmStatic
        @DynamicPropertySource
        fun overridePgUrl(registry: DynamicPropertyRegistry) {
            server.start()
            registry.add("pg-simulator.url") { "http://localhost:${server.port}" }
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            server.shutdown()
        }
    }
}
