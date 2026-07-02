package com.loopers.infrastructure.payment

import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PgClient
import com.loopers.domain.payment.PgPaymentCommand
import com.loopers.domain.payment.PgUnavailableException
import com.ninjasquad.springmockk.MockkBean
import feign.FeignException
import feign.Request
import feign.Response
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.nio.charset.StandardCharsets

/**
 * Resilience4j(@Retry + @CircuitBreaker + fallback) 가 PgClientImpl 에 실제로 적용되는지 검증한다.
 * 외부 Feign 호출(PgFeignClient)을 모킹해 5xx 실패를 주입하고, 재시도/서킷오픈/폴백 동작을 확인한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PgClientResilienceE2ETest {
    @Autowired
    lateinit var pgClient: PgClient

    @Autowired
    lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @MockkBean
    lateinit var pgFeignClient: PgFeignClient

    @BeforeEach
    fun resetCircuit() {
        circuitBreakerRegistry.circuitBreaker("pgClient").reset()
    }

    private fun command() = PgPaymentCommand(
        userId = "1",
        orderId = "000001",
        cardType = CardType.SAMSUNG,
        cardNo = "1234-5678-9814-1451",
        amount = 5_000,
        callbackUrl = "http://localhost:8080/api/v1/payments/callback",
    )

    private fun serverError(): FeignException {
        val request = Request.create(
            Request.HttpMethod.POST,
            "http://localhost:8082/api/v1/payments",
            emptyMap(),
            null,
            StandardCharsets.UTF_8,
            null,
        )
        val response = Response.builder()
            .status(500)
            .reason("Internal Server Error")
            .request(request)
            .build()
        return FeignException.errorStatus("PgFeignClient#requestPayment", response)
    }

    @DisplayName("5xx 가 반복되면, 설정된 최대 횟수(3회)만큼 재시도한 뒤 fallback 으로 PgUnavailableException 을 던진다.")
    @Test
    fun retriesUpToMaxAttemptsThenFallback() {
        every { pgFeignClient.requestPayment(any(), any()) } throws serverError()

        assertThrows<PgUnavailableException> { pgClient.requestPayment(command()) }

        verify(exactly = 3) { pgFeignClient.requestPayment(any(), any()) }
    }

    @DisplayName("실패가 누적되면 서킷이 Open 되고, 이후 호출은 외부를 호출하지 않고 fallback 으로 보호된다.")
    @Test
    fun opensCircuitAndShortCircuits() {
        every { pgFeignClient.requestPayment(any(), any()) } throws serverError()

        // 실패를 누적시켜 서킷을 Open 으로 만든다.
        repeat(4) { runCatching { pgClient.requestPayment(command()) } }

        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("pgClient")
        assertThat(circuitBreaker.state).isEqualTo(CircuitBreaker.State.OPEN)

        // 호출 이력 초기화 후, Open 상태에서의 호출은 외부 호출 없이 즉시 fallback 으로 막힌다.
        clearMocks(pgFeignClient)
        every { pgFeignClient.requestPayment(any(), any()) } throws serverError()

        assertThrows<PgUnavailableException> { pgClient.requestPayment(command()) }
        verify(exactly = 0) { pgFeignClient.requestPayment(any(), any()) }
    }
}
