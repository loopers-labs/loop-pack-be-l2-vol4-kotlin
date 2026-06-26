package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.order.application.OrderFacade
import com.loopers.domain.order.application.service.OrderService
import com.loopers.domain.order.model.OrderStatus
import com.loopers.domain.order.support.OrderSteps.Companion.주문_생성_커맨드
import com.loopers.domain.order.support.OrderSteps.Companion.주문항목_생성_커맨드
import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.기본_로그인_ID
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer

class PaymentApiE2ETest
    @Autowired
    constructor(
        private val userService: UserService,
        private val brandService: BrandService,
        private val productFacade: ProductFacade,
        private val orderFacade: OrderFacade,
        private val orderService: OrderService,
    ) : ApiTest() {
        private val mapResponseType =
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}

        @BeforeEach
        fun setUp() {
            pgServer.reset()
        }

        @Test
        fun `결제_요청은_PG에_요청하고_거래키를_저장한다`() {
            val userId = userService.signUp(사용자_회원가입()).id
            val orderId = placePendingOrder(userId)

            val response = requestPayment(orderId = orderId)

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.number("orderId")).isEqualTo(orderId)
            assertThat(response.body?.data?.get("transactionKey")).isEqualTo("20260625:TR:test01")
            assertThat(response.body?.data?.get("status")).isEqualTo("REQUESTED")
            assertThat(pgServer.requests).hasSize(1)
            assertThat(pgServer.requests.first()).contains(""""orderId":"$orderId"""")
        }

        @Test
        fun `승인_콜백은_주문을_ORDERED로_전이한다`() {
            val userId = userService.signUp(사용자_회원가입()).id
            val orderId = placePendingOrder(userId)
            requestPayment(orderId = orderId)

            val response = callback(status = "SUCCESS")

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.get("status")).isEqualTo("APPROVED")
            assertThat(orderService.getById(orderId).status).isEqualTo(OrderStatus.ORDERED)
        }

        @Test
        fun `인증_헤더가_없으면_401을_반환한다`() {
            val response = requestPayment(orderId = 1L, headers = HttpHeaders())

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `콜백_시크릿_헤더가_없으면_401을_반환한다`() {
            val response = callback(status = "SUCCESS", secret = null)

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `콜백_시크릿_헤더가_틀리면_401을_반환한다`() {
            val response = callback(status = "SUCCESS", secret = "wrong-secret")

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @Test
        fun `타인의_주문을_결제하면_404를_반환한다`() {
            val ownerId = userService.signUp(사용자_회원가입()).id
            userService.signUp(사용자_회원가입(loginId = "other1234", email = "other@example.com"))
            val orderId = placePendingOrder(ownerId)

            val response = requestPayment(orderId = orderId, headers = authHeaders(loginId = "other1234"))

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @Test
        fun `카드번호_형식이_잘못되면_400을_반환한다`() {
            userService.signUp(사용자_회원가입())

            val response = requestPayment(orderId = 1L, cardNo = "1234", headers = authHeaders())

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        private fun placePendingOrder(userId: Long): Long {
            val brand = brandService.register(브랜드_등록_커맨드())
            val productId = productFacade.registerProduct(
                상품_등록_커맨드(
                    brandId = brand.id,
                    price = 10_000,
                    initialStock = 5,
                ),
            ).id
            return orderFacade.placeOrder(
                주문_생성_커맨드(
                    userId = userId,
                    items = listOf(주문항목_생성_커맨드(productId = productId, quantity = 1)),
                ),
            ).id
        }

        private fun requestPayment(
            orderId: Long,
            cardNo: String = "1234-5678-1234-5678",
            headers: HttpHeaders = authHeaders(),
        ) = testRestTemplate.exchange(
            "/api/v1/payments",
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "orderId" to orderId,
                    "cardType" to "SAMSUNG",
                    "cardNo" to cardNo,
                ),
                headers,
            ),
            mapResponseType,
        )

        private fun callback(
            status: String,
            secret: String? = 콜백_시크릿,
        ) = testRestTemplate.exchange(
            "/api/v1/payments/callback",
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "transactionKey" to "20260625:TR:test01",
                    "status" to status,
                    "reason" to null,
                ),
                HttpHeaders().apply { secret?.let { set("X-Payment-Callback-Secret", it) } },
            ),
            mapResponseType,
        )

        private fun Map<String, Any?>.number(key: String): Long =
            (get(key) as Number).toLong()

        private fun authHeaders(
            loginId: String = 기본_로그인_ID,
            rawPassword: String = 기본_비밀번호,
        ): HttpHeaders {
            val headers = HttpHeaders()
            headers.set("X-Loopers-LoginId", loginId)
            headers.set("X-Loopers-LoginPw", rawPassword)
            return headers
        }

        companion object {
            private const val 콜백_시크릿 = "local-callback-secret"
            private val pgServer = PgStubServer()

            @JvmStatic
            @DynamicPropertySource
            fun pgProperties(registry: DynamicPropertyRegistry) {
                pgServer.start()
                registry.add("payment.pg-simulator.base-url") { pgServer.baseUrl }
                registry.add("payment.callback-secret") { 콜백_시크릿 }
            }
        }

        private class PgStubServer {
            private val server: HttpServer = HttpServer.create(InetSocketAddress(0), 0)
            private var started: Boolean = false
            val requests: MutableList<String> = CopyOnWriteArrayList()
            val baseUrl: String
                get() = "http://localhost:${server.address.port}"

            fun start() {
                if (started) {
                    return
                }
                server.createContext("/api/v1/payments") { exchange -> handle(exchange) }
                server.start()
                started = true
            }

            fun reset() {
                requests.clear()
            }

            private fun handle(exchange: HttpExchange) {
                val response = when (exchange.requestMethod) {
                    "POST" -> {
                        requests.add(exchange.requestBody.bufferedReader().readText())
                        """
                        {"meta":{"result":"SUCCESS","errorCode":null,"message":null},"data":{"transactionKey":"20260625:TR:test01","status":"PENDING","reason":null}}
                        """.trimIndent()
                    }
                    else -> """
                        {"meta":{"result":"SUCCESS","errorCode":null,"message":null},"data":{"orderId":"10","transactions":[{"transactionKey":"20260625:TR:test01","status":"PENDING","reason":null}]}}
                    """.trimIndent()
                }
                exchange.responseHeaders.add("Content-Type", "application/json")
                val bytes = response.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
    }
