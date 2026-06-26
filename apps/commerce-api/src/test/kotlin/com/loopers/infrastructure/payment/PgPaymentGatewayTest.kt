package com.loopers.infrastructure.payment

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentStatus
import com.loopers.domain.order.OrderAmount
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.net.ServerSocket
import java.time.Duration

class PgPaymentGatewayTest {

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var pgPaymentGateway: PgPaymentGateway

    @BeforeEach
    fun setUp() {
        val restClientBuilder = RestClient.builder().baseUrl("http://localhost:8082")
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build()
        pgPaymentGateway = PgPaymentGateway(restClientBuilder.build())
    }

    @DisplayName("PG 결제 요청 시, ")
    @Nested
    inner class Pay {
        @DisplayName("PG에 결제 요청을 보내면 transactionKey와 PENDING 상태를 반환한다")
        @Test
        fun pay_returnsPendingResult_whenPgRespondsSuccessfully() {
            // arrange
            val command = newPaymentCommand()

            mockServer.expect(requestTo("http://localhost:8082/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                    withSuccess(
                        """
                        {
                            "meta": {
                                "result": "SUCCESS",
                                "errorCode": null,
                                "message": null
                            },
                            "data": {
                                "transactionKey": "20250623:TR:abc123",
                                "status": "PENDING",
                                "reason": null
                            }
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            // act
            val result = pgPaymentGateway.pay(command)

            // assert
            assertThat(result.transactionKey).isEqualTo("20250623:TR:abc123")
            assertThat(result.status).isEqualTo(PaymentStatus.PENDING)
            mockServer.verify()
        }
    }

    @DisplayName("Read Timeout 시, ")
    @Nested
    inner class ReadTimeout {
        private lateinit var slowServer: ServerSocket

        @BeforeEach
        fun setUp() {
            slowServer = ServerSocket(0)
        }

        @AfterEach
        fun tearDown() {
            slowServer.close()
        }

        @DisplayName("PG 서버가 응답하지 않으면 ResourceAccessException이 발생한다")
        @Test
        fun pay_throwsResourceAccessException_whenPgDoesNotRespond() {
            // arrange — 연결은 받지만 응답하지 않는 서버
            val acceptThread = Thread {
                try {
                    val socket = slowServer.accept()
                    Thread.sleep(5_000)
                    socket.close()
                } catch (_: Exception) {
                }
            }
            acceptThread.isDaemon = true
            acceptThread.start()

            val settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(1))
                .withReadTimeout(Duration.ofSeconds(1))

            val restClient = RestClient.builder()
                .baseUrl("http://localhost:${slowServer.localPort}")
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build()

            val timeoutGateway = PgPaymentGateway(restClient)

            // act & assert
            assertThrows<org.springframework.web.client.ResourceAccessException> {
                timeoutGateway.pay(newPaymentCommand())
            }
        }
    }

    @DisplayName("CircuitBreaker 시, ")
    @Nested
    inner class CircuitBreakerTest {
        @DisplayName("연속 실패가 임계치를 넘으면 OPEN 상태가 되어 호출을 즉시 차단한다")
        @Test
        fun pay_rejectsCall_whenCircuitBreakerIsOpen() {
            // arrange — 5번 연속 실패하는 MockServer 설정
            val restClientBuilder = RestClient.builder().baseUrl("http://localhost:8082")
            val server = MockRestServiceServer.bindTo(restClientBuilder).build()

            val circuitBreaker = io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                .failureRateThreshold(50f)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(5)
                .build()
            val cb = io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry.of(circuitBreaker)
                .circuitBreaker("test-pg")

            val noRetryConfig = io.github.resilience4j.retry.RetryConfig.custom<Any>()
                .maxAttempts(1)
                .build()
            val noRetry = io.github.resilience4j.retry.RetryRegistry.of(noRetryConfig)
                .retry("no-retry-cb-test")
            val gateway = PgPaymentGateway(restClientBuilder.build(), cb, noRetry)

            // 5번 연속 실패 (서버 에러 응답)
            repeat(5) {
                server.expect(requestTo("http://localhost:8082/api/v1/payments"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(
                        org.springframework.test.web.client.response.MockRestResponseCreators
                            .withServerError(),
                    )
            }

            repeat(5) {
                runCatching { gateway.pay(newPaymentCommand()) }
            }

            // act — 6번째 호출은 PG에 요청조차 하지 않고 즉시 차단
            val exception = assertThrows<CoreException> {
                gateway.pay(newPaymentCommand())
            }

            // assert
            assertThat(cb.state).isEqualTo(io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN)
            assertThat(exception.errorType).isEqualTo(ErrorType.SERVICE_UNAVAILABLE)
        }
    }

    @DisplayName("Retry 시, ")
    @Nested
    inner class RetryTest {
        @DisplayName("조회 API에서 5xx 서버 에러가 발생하면 재시도하여 성공을 반환한다")
        @Test
        fun getTransactionStatus_retriesOn5xx_andSucceeds() {
            // arrange
            val restClientBuilder = RestClient.builder().baseUrl("http://localhost:8082")
            val server = MockRestServiceServer.bindTo(restClientBuilder).build()
            val gateway = PgPaymentGateway(
                restClient = restClientBuilder.build(),
                retry = newRetry("test-retry-5xx"),
            )

            server.expect(requestTo("http://localhost:8082/api/v1/payments/TR001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                    org.springframework.test.web.client.response.MockRestResponseCreators
                        .withServerError(),
                )
            server.expect(requestTo("http://localhost:8082/api/v1/payments/TR001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                    withSuccess(
                        """
                        {
                            "meta": {"result": "SUCCESS", "errorCode": null, "message": null},
                            "data": {"transactionKey": "TR001", "orderId": "100001", "cardType": "SAMSUNG", "cardNo": "1234-5678-9012-3456", "amount": 5000, "status": "SUCCESS", "reason": null}
                        }
                        """.trimIndent(),
                        MediaType.APPLICATION_JSON,
                    ),
                )

            // act
            val result = gateway.getTransactionStatus("TR001")

            // assert
            assertThat(result.transactionKey).isEqualTo("TR001")
            assertThat(result.status).isEqualTo(PaymentStatus.SUCCESS)
            server.verify()
        }

        @DisplayName("조회 API에서 4xx 클라이언트 에러가 발생하면 재시도하지 않고 즉시 실패한다")
        @Test
        fun getTransactionStatus_doesNotRetryOn4xx() {
            // arrange
            val restClientBuilder = RestClient.builder().baseUrl("http://localhost:8082")
            val server = MockRestServiceServer.bindTo(restClientBuilder).build()
            val gateway = PgPaymentGateway(
                restClient = restClientBuilder.build(),
                retry = newRetry("test-no-retry-4xx"),
            )

            // 1번만 기대 — 4xx는 재시도 없이 바로 실패해야 함
            server.expect(requestTo("http://localhost:8082/api/v1/payments/TR001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                    org.springframework.test.web.client.response.MockRestResponseCreators
                        .withResourceNotFound(),
                )

            // act & assert
            assertThrows<org.springframework.web.client.HttpClientErrorException> {
                gateway.getTransactionStatus("TR001")
            }
            server.verify()
        }

        @DisplayName("결제 요청(pay)은 5xx가 발생해도 재시도하지 않는다")
        @Test
        fun pay_doesNotRetry_onFailure() {
            // arrange
            val restClientBuilder = RestClient.builder().baseUrl("http://localhost:8082")
            val server = MockRestServiceServer.bindTo(restClientBuilder).build()
            val gateway = PgPaymentGateway(
                restClient = restClientBuilder.build(),
                retry = newRetry("test-no-retry-pay"),
            )

            // 1번만 기대 — 재시도 없이 바로 실패해야 함
            server.expect(requestTo("http://localhost:8082/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                    org.springframework.test.web.client.response.MockRestResponseCreators
                        .withServerError(),
                )

            // act & assert
            assertThrows<org.springframework.web.client.HttpServerErrorException> {
                gateway.pay(newPaymentCommand())
            }
            server.verify()
        }

        private fun newRetry(name: String): io.github.resilience4j.retry.Retry {
            val config = io.github.resilience4j.retry.RetryConfig.custom<Any>()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(100))
                .retryExceptions(
                    org.springframework.web.client.ResourceAccessException::class.java,
                    org.springframework.web.client.HttpServerErrorException::class.java,
                )
                .ignoreExceptions(
                    org.springframework.web.client.HttpClientErrorException::class.java,
                )
                .build()
            return io.github.resilience4j.retry.RetryRegistry.of(config).retry(name)
        }
    }

    private fun newPaymentCommand() = PaymentCommand(
        orderId = 100001L,
        userId = 1L,
        amount = OrderAmount(5000L),
        cardType = "SAMSUNG",
        cardNo = "1234-5678-9012-3456",
        callbackUrl = "http://localhost:8080/api/v1/payments/callback",
    )
}
