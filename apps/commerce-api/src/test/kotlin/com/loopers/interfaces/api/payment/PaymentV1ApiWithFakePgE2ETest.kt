package com.loopers.interfaces.api.payment

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.payment.PgTransactionStatus
import com.loopers.domain.user.PasswordEncoder
import com.loopers.infrastructure.brand.entity.BrandEntity
import com.loopers.infrastructure.brand.repository.BrandJpaRepository
import com.loopers.infrastructure.inventory.entity.InventoryEntity
import com.loopers.infrastructure.inventory.repository.InventoryJpaRepository
import com.loopers.infrastructure.member.entity.MemberEntity
import com.loopers.infrastructure.member.repository.MemberJpaRepository
import com.loopers.infrastructure.order.repository.OrderJpaRepository
import com.loopers.infrastructure.payment.repository.PaymentJpaRepository
import com.loopers.infrastructure.product.entity.ProductEntity
import com.loopers.infrastructure.product.repository.ProductJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.dto.OrderV1Dto
import com.loopers.interfaces.api.payment.dto.PaymentV1Dto
import com.loopers.utils.DatabaseCleanUp
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
    properties = [
        "server.port=8080",
        "pg.payment.callback-url=http://localhost:8080/api/v1/payments/callback",
    ],
)
class PaymentV1ApiWithFakePgE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val memberJpaRepository: MemberJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val inventoryJpaRepository: InventoryJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val paymentJpaRepository: PaymentJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @BeforeEach
    fun setUp() {
        fakePgServer.reset()
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @AfterAll
    fun stopFakePgServer() {
        fakePgServer.stop()
    }

    @DisplayName("PG 성공 콜백을 받으면 결제를 성공시키고 주문을 완료한다")
    @Test
    fun completesOrderWhenPgCallbackSucceeds() {
        val flow = requestPayment()

        fakePgServer.complete(
            transactionKey = requireNotNull(flow.acceptedPayment.transactionKey),
            status = PgTransactionStatus.SUCCESS,
        )

        val finalPayment = awaitTerminalPayment(flow.acceptedPayment.paymentId)
        val savedOrder = orderJpaRepository.findWithItemsById(flow.order.orderId)
        val savedInventory = inventoryJpaRepository.findByProductId(flow.product.id)

        assertAll(
            { assertThat(flow.acceptedPayment.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(finalPayment.status).isEqualTo(PaymentStatus.SUCCESS) },
            { assertThat(savedOrder?.status).isEqualTo(OrderStatus.COMPLETED) },
            { assertThat(savedInventory?.quantity).isEqualTo(9L) },
        )
    }

    @DisplayName("PG 실패 콜백을 받으면 결제를 실패시키고 주문 예약을 복구한다")
    @Test
    fun restoresReservationWhenPgCallbackFails() {
        val flow = requestPayment()

        fakePgServer.complete(
            transactionKey = requireNotNull(flow.acceptedPayment.transactionKey),
            status = PgTransactionStatus.FAILED,
            reason = "Fake PG rejected the payment.",
        )

        val finalPayment = awaitTerminalPayment(flow.acceptedPayment.paymentId)
        val savedOrder = orderJpaRepository.findWithItemsById(flow.order.orderId)
        val savedInventory = inventoryJpaRepository.findByProductId(flow.product.id)

        assertAll(
            { assertThat(flow.acceptedPayment.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(finalPayment.status).isEqualTo(PaymentStatus.FAILED) },
            { assertThat(finalPayment.reason).isEqualTo("Fake PG rejected the payment.") },
            { assertThat(savedOrder?.status).isEqualTo(OrderStatus.PAYMENT_FAILED) },
            { assertThat(savedInventory?.quantity).isEqualTo(10L) },
        )
    }

    @DisplayName("같은 멱등키로 결제 요청을 반복하면 기존 결제를 반환한다")
    @Test
    fun returnsExistingPaymentWhenSameIdempotencyKeyIsRepeated() {
        val member = createMember()
        val brand = createBrand()
        val product = createProduct(brandId = brand.id, price = 10_000L)
        createInventory(productId = product.id, quantity = 10L)
        val order = placeOrder(productId = product.id, quantity = 1L)
        val idempotencyKey = "payment-order-${order.orderId}"
        val firstPayment = requestPayment(orderId = order.orderId, idempotencyKey = idempotencyKey)

        val secondPayment = requestPayment(orderId = order.orderId, idempotencyKey = idempotencyKey)

        assertAll(
            { assertThat(firstPayment.status).isEqualTo(PaymentStatus.PENDING) },
            { assertThat(secondPayment.paymentId).isEqualTo(firstPayment.paymentId) },
            {
                assertThat(
                    paymentJpaRepository.findByMemberIdAndIdempotencyKey(
                        memberId = member.id,
                        idempotencyKey = idempotencyKey,
                    )?.id,
                ).isEqualTo(firstPayment.paymentId)
            },
        )
    }

    private fun requestPayment(): PaymentFlow {
        createMember()
        val brand = createBrand()
        val product = createProduct(brandId = brand.id, price = 10_000L)
        createInventory(productId = product.id, quantity = 10L)
        val order = placeOrder(productId = product.id, quantity = 1L)
        val payment = requestPayment(
            orderId = order.orderId,
            idempotencyKey = "payment-order-${order.orderId}",
        )

        return PaymentFlow(
            order = order,
            product = product,
            acceptedPayment = payment,
        )
    }

    private fun placeOrder(productId: Long, quantity: Long): OrderV1Dto.OrderResponse {
        val response = testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            HttpEntity(
                OrderV1Dto.CreateOrderRequest(
                    items = listOf(
                        OrderV1Dto.CreateOrderRequest.Item(productId = productId, quantity = quantity),
                    ),
                ),
                createAuthHeaders(),
            ),
            object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        return requireNotNull(response.body?.data)
    }

    private fun requestPayment(orderId: Long, idempotencyKey: String): PaymentV1Dto.PaymentResponse {
        val response = testRestTemplate.exchange(
            "/api/v1/payments",
            HttpMethod.POST,
            HttpEntity(
                PaymentV1Dto.PaymentRequest(
                    orderId = orderId,
                    cardType = CardType.SAMSUNG,
                    cardNo = "1234-5678-9814-1451",
                ),
                createAuthHeaders().apply {
                    set("Idempotency-Key", idempotencyKey)
                },
            ),
            object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        return requireNotNull(response.body?.data)
    }

    private fun awaitTerminalPayment(paymentId: Long): PaymentV1Dto.PaymentResponse {
        val deadline = System.nanoTime() + CALLBACK_WAIT_TIMEOUT.toNanos()
        var latest: PaymentV1Dto.PaymentResponse? = null

        while (System.nanoTime() < deadline) {
            latest = getPayment(paymentId)
            if (latest.status in setOf(PaymentStatus.SUCCESS, PaymentStatus.FAILED)) {
                return latest
            }
            Thread.sleep(POLL_INTERVAL.toMillis())
        }

        throw AssertionError("Payment did not become terminal. latest=$latest")
    }

    private fun getPayment(paymentId: Long): PaymentV1Dto.PaymentResponse {
        val response = testRestTemplate.exchange(
            "/api/v1/payments/$paymentId",
            HttpMethod.GET,
            HttpEntity<Any>(null, createAuthHeaders()),
            object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PaymentResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        return requireNotNull(response.body?.data)
    }

    private fun createMember(
        loginId: String = LOGIN_ID,
        password: String = RAW_PASSWORD,
    ): MemberEntity {
        return memberJpaRepository.save(
            MemberEntity(
                loginId = loginId,
                password = PasswordEncoder.encode(password),
                name = "홍길동",
                birthDate = LocalDate.of(1990, 1, 1),
                email = "$loginId@example.com",
            ),
        )
    }

    private fun createBrand(): BrandEntity {
        return brandJpaRepository.save(
            BrandEntity(
                name = "loopers",
                description = "loopers brand",
                logoImageUrl = "https://image.loopers/brand.png",
            ),
        )
    }

    private fun createProduct(
        brandId: Long,
        price: Long,
    ): ProductEntity {
        return productJpaRepository.save(
            ProductEntity(
                brandId = brandId,
                name = "loopers hoodie",
                price = price,
                description = "loopers product",
                imageUrl = "https://image.loopers/product.png",
            ),
        )
    }

    private fun createInventory(productId: Long, quantity: Long): InventoryEntity {
        return inventoryJpaRepository.save(
            InventoryEntity(
                productId = productId,
                quantity = quantity,
            ),
        )
    }

    private fun createAuthHeaders(): HttpHeaders {
        return HttpHeaders().apply {
            set("X-Loopers-LoginId", LOGIN_ID)
            set("X-Loopers-LoginPw", RAW_PASSWORD)
        }
    }

    private data class PaymentFlow(
        val order: OrderV1Dto.OrderResponse,
        val product: ProductEntity,
        val acceptedPayment: PaymentV1Dto.PaymentResponse,
    )

    private companion object {
        private const val LOGIN_ID = "loopers123"
        private const val RAW_PASSWORD = "Loopers123!"
        private val CALLBACK_WAIT_TIMEOUT: Duration = Duration.ofSeconds(3)
        private val POLL_INTERVAL: Duration = Duration.ofMillis(100)
        private val fakePgServer = FakePgServer()

        @JvmStatic
        @DynamicPropertySource
        fun registerPgProperties(registry: DynamicPropertyRegistry) {
            fakePgServer.start()
            registry.add("pg.payment.base-url", fakePgServer::baseUrl)
        }
    }
}

private class FakePgServer {
    private val objectMapper = jacksonObjectMapper()
    private val transactions = ConcurrentHashMap<String, FakeTransaction>()
    private val transactionSequence = AtomicLong()
    private val httpClient = HttpClient.newHttpClient()
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/api/v1/payments") { exchange ->
            when (exchange.requestMethod) {
                "POST" -> handlePaymentRequest(exchange)
                "GET" -> handlePaymentSearch(exchange)
                else -> exchange.sendJson(HttpURLConnection.HTTP_BAD_METHOD, mapOf("data" to null))
            }
        }
        executor = this@FakePgServer.executor
    }
    private var started = false

    fun start() {
        if (!started) {
            server.start()
            started = true
        }
    }

    fun stop() {
        if (started) {
            server.stop(0)
            executor.shutdownNow()
            started = false
        }
    }

    fun reset() {
        transactions.clear()
    }

    fun baseUrl(): String {
        return "http://localhost:${server.address.port}"
    }

    fun complete(
        transactionKey: String,
        status: PgTransactionStatus,
        reason: String? = null,
    ) {
        val transaction = transactions[transactionKey]
            ?: throw AssertionError("Fake PG transaction not found. transactionKey=$transactionKey")
        transaction.status = status
        transaction.reason = reason

        val callbackRequest = FakeCallbackRequest(
            transactionKey = transaction.transactionKey,
            orderId = transaction.orderId,
            cardType = transaction.cardType,
            cardNo = transaction.cardNo,
            amount = transaction.amount,
            status = status,
            reason = reason,
        )
        val request = HttpRequest.newBuilder(URI.create(transaction.callbackUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(callbackRequest)))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(HttpURLConnection.HTTP_OK)
    }

    private fun handlePaymentRequest(exchange: HttpExchange) {
        val request = objectMapper.readValue<FakePaymentRequest>(
            exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).readText(),
        )
        val transaction = FakeTransaction(
            transactionKey = "fake-pg-${transactionSequence.incrementAndGet()}",
            orderId = request.orderId,
            cardType = request.cardType,
            cardNo = request.cardNo,
            amount = request.amount,
            callbackUrl = request.callbackUrl,
            status = PgTransactionStatus.PENDING,
            reason = null,
        )
        transactions[transaction.transactionKey] = transaction

        exchange.sendJson(
            statusCode = HttpURLConnection.HTTP_OK,
            body = mapOf(
                "data" to FakeTransactionResponse(
                    transactionKey = transaction.transactionKey,
                    status = transaction.status,
                    reason = transaction.reason,
                ),
            ),
        )
    }

    private fun handlePaymentSearch(exchange: HttpExchange) {
        val orderId = exchange.requestURI.rawQuery
            ?.split("&")
            ?.mapNotNull { query ->
                val parts = query.split("=", limit = 2)
                if (parts.size == 2) {
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                } else {
                    null
                }
            }
            ?.toMap()
            ?.get("orderId")

        val matchingTransactions = transactions.values
            .filter { transaction -> transaction.orderId == orderId }
            .map { transaction ->
                FakeTransactionResponse(
                    transactionKey = transaction.transactionKey,
                    status = transaction.status,
                    reason = transaction.reason,
                )
            }

        exchange.sendJson(
            statusCode = HttpURLConnection.HTTP_OK,
            body = mapOf(
                "data" to mapOf(
                    "orderId" to orderId,
                    "transactions" to matchingTransactions,
                ),
            ),
        )
    }

    private fun HttpExchange.sendJson(statusCode: Int, body: Any) {
        val response = objectMapper.writeValueAsBytes(body)
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(statusCode, response.size.toLong())
        responseBody.use { outputStream -> outputStream.write(response) }
    }

    private data class FakePaymentRequest(
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String,
    )

    private data class FakeCallbackRequest(
        val transactionKey: String,
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val status: PgTransactionStatus,
        val reason: String?,
    )

    private data class FakeTransactionResponse(
        val transactionKey: String,
        val status: PgTransactionStatus,
        val reason: String?,
    )

    private data class FakeTransaction(
        val transactionKey: String,
        val orderId: String,
        val cardType: CardType,
        val cardNo: String,
        val amount: Long,
        val callbackUrl: String,
        var status: PgTransactionStatus,
        var reason: String?,
    )
}
