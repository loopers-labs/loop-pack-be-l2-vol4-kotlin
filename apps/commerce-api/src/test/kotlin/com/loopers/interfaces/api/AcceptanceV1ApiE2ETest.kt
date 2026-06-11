package com.loopers.interfaces.api

import com.loopers.domain.catalog.ProductStats
import com.loopers.domain.order.OrderCancelReason
import com.loopers.domain.order.OrderCommand
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.order.StockReservationStatus
import com.loopers.domain.user.PasswordEncoder
import com.loopers.domain.user.RawPassword
import com.loopers.domain.user.User
import com.loopers.domain.user.UserRole
import com.loopers.application.order.OrderCheckoutFacade
import com.loopers.infrastructure.catalog.ProductStatsJpaRepository
import com.loopers.infrastructure.catalog.ProductStockJpaRepository
import com.loopers.infrastructure.like.ProductLikeHistoryJpaRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.order.StockReservationJpaRepository
import com.loopers.infrastructure.payment.FakePaymentGateway
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.interfaces.api.catalog.CatalogV1Dto
import com.loopers.interfaces.api.like.LikeV1Dto
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcceptanceV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val userJpaRepository: UserJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val productStockJpaRepository: ProductStockJpaRepository,
    private val productStatsJpaRepository: ProductStatsJpaRepository,
    private val productLikeHistoryJpaRepository: ProductLikeHistoryJpaRepository,
    private val stockReservationJpaRepository: StockReservationJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val orderCheckoutFacade: OrderCheckoutFacade,
    private val paymentGateway: FakePaymentGateway,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @BeforeEach
    fun setUp() {
        paymentGateway.reset()
    }

    @AfterEach
    fun tearDown() {
        paymentGateway.reset()
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("A-01. A consumer can read registered products and brand with latest, price, and like sorting")
    @Test
    fun readsRegisteredCatalogWithSortingAndBrandDetail() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        saveUser("consumer02")
        val brand = createBrand("Nike")
        val first = createProduct(brand.brandId, "Pegasus", 129000L, 5, listOf("https://cdn.example.com/pegasus.png"))
        Thread.sleep(10)
        val second = createProduct(brand.brandId, "Cortez", 89000L, 3, listOf("https://cdn.example.com/cortez.png"))
        Thread.sleep(10)
        val third = createProduct(brand.brandId, "Dunk", 159000L, 7, listOf("https://cdn.example.com/dunk.png"))
        registerLike("consumer01", second.productId)
        registerLike("consumer01", third.productId)
        registerLike("consumer02", third.productId)

        val latest = get<List<CatalogV1Dto.ProductDisplayResponse>>("/api/v1/products?sort=latest&page=0&size=20")
        val priceAsc = get<List<CatalogV1Dto.ProductDisplayResponse>>("/api/v1/products?sort=price_asc&page=0&size=20")
        val likesDesc = get<List<CatalogV1Dto.ProductDisplayResponse>>("/api/v1/products?sort=likes_desc&page=0&size=20")
        val detail = get<CatalogV1Dto.ProductDetailResponse>("/api/v1/products/${first.productId}")
        val brandDetail = get<BrandDetailResponse>("/api/v1/brands/${brand.brandId}")

        assertAll(
            { assertThat(latest.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(latest.body?.data?.map { it.productId }).containsExactly(third.productId, second.productId, first.productId) },
            { assertThat(priceAsc.body?.data?.map { it.productId }).containsExactly(second.productId, first.productId, third.productId) },
            { assertThat(likesDesc.body?.data?.map { it.productId }).containsExactly(third.productId, second.productId, first.productId) },
            { assertThat(likesDesc.body?.data?.map { it.likeCount }).containsExactly(2L, 1L, 0L) },
            { assertThat(detail.body?.data?.product?.productName).isEqualTo("Pegasus") },
            { assertThat(detail.body?.data?.product?.brandName).isEqualTo("Nike") },
            { assertThat(detail.body?.data?.detailImages).containsExactly("https://cdn.example.com/pegasus.png") },
            { assertThat(brandDetail.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(brandDetail.body?.data?.products?.map { it.productId }).contains(first.productId, second.productId, third.productId) },
            { assertThat(stockQuantity(first.productId)).isEqualTo(5) },
        )
    }

    @DisplayName("A-02. Product like register and cancel are idempotent and visible in catalog reads")
    @Test
    fun productLikeRegisterAndCancelAreIdempotent() {
        saveUser("consumer01")
        saveUser("admin01", UserRole.ADMIN)
        val brand = createBrand("Nike")
        val product = createProduct(brand.brandId, "Pegasus", 129000L, 5)

        post<LikeV1Dto.LikeResponse>("/api/v1/products/${product.productId}/likes", null, authHeaders("consumer01"))
        val duplicateRegister = post<LikeV1Dto.LikeResponse>("/api/v1/products/${product.productId}/likes", null, authHeaders("consumer01"))
        val likedDetail = get<CatalogV1Dto.ProductDetailResponse>("/api/v1/products/${product.productId}", authHeaders("consumer01"))
        val likeCountAfterRegister = productStats(product.productId).likeCount
        delete<LikeV1Dto.LikeResponse>("/api/v1/products/${product.productId}/likes", authHeaders("consumer01"))
        val duplicateCancel = delete<LikeV1Dto.LikeResponse>("/api/v1/products/${product.productId}/likes", authHeaders("consumer01"))
        val unlikedDetail = get<CatalogV1Dto.ProductDetailResponse>("/api/v1/products/${product.productId}", authHeaders("consumer01"))

        assertAll(
            { assertThat(duplicateRegister.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(duplicateRegister.body?.data?.liked).isTrue() },
            { assertThat(likedDetail.body?.data?.product?.likedByMe).isTrue() },
            { assertThat(likeCountAfterRegister).isEqualTo(1L) },
            { assertThat(duplicateCancel.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(duplicateCancel.body?.data?.liked).isFalse() },
            { assertThat(unlikedDetail.body?.data?.product?.likedByMe).isFalse() },
            { assertThat(productStats(product.productId).likeCount).isEqualTo(0L) },
            { assertThat(productLikeHistoryJpaRepository.countByUserIdAndProductId(userId("consumer01"), product.productId)).isEqualTo(2L) },
        )
    }

    @DisplayName("A-03. Adding to cart checks stock but does not reserve it")
    @Test
    fun cartAddChecksStockButDoesNotReserveIt() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        saveUser("consumer02")
        val brand = createBrand("Nike")
        val product = createProduct(brand.brandId, "Pegasus", 129000L, 1)

        addCartItem("consumer01", product.productId, 1)
        val consumerTwoCheckout = checkoutCart("consumer02", product.productId)
        val consumerOneCheckout = checkoutCart("consumer01")

        assertAll(
            { assertThat(stockQuantity(product.productId)).isEqualTo(1) },
            { assertThat(activeReservedQuantity(product.productId)).isEqualTo(1) },
            { assertThat(consumerTwoCheckout.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(consumerOneCheckout.statusCode).isEqualTo(HttpStatus.CONFLICT) },
        )
    }

    @DisplayName("A-04. Checkout creates a payment-pending order with item snapshots and active stock reservations")
    @Test
    fun checkoutCreatesPendingOrderAndStockReservations() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        val brand = createBrand("Nike")
        val first = createProduct(brand.brandId, "Pegasus", 129000L, 3)
        val second = createProduct(brand.brandId, "Dunk", 159000L, 2)
        addCartItem("consumer01", first.productId, 2)
        addCartItem("consumer01", second.productId, 1)

        val checkout = checkoutCart("consumer01")

        assertAll(
            { assertThat(checkout.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(checkout.body?.data?.status).isEqualTo(OrderStatus.PAYMENT_PENDING) },
            { assertThat(checkout.body?.data?.items).hasSize(2) },
            { assertThat(checkout.body?.data?.items?.map { it.quantity }).containsExactly(2, 1) },
            { assertThat(checkout.body?.data?.deliveryAddress).isEqualTo("Seoul") },
            { assertThat(checkout.body?.data?.deliveryRequest).isEqualTo("Front door") },
            { assertThat(checkout.body?.data?.phoneNumber).isEqualTo("010-1234-5678") },
            { assertThat(activeReservedQuantity(first.productId)).isEqualTo(2) },
            { assertThat(activeReservedQuantity(second.productId)).isEqualTo(1) },
            { assertThat(stockQuantity(first.productId)).isEqualTo(3) },
            { assertThat(stockQuantity(second.productId)).isEqualTo(2) },
        )
    }

    @DisplayName("A-05. Payment completion retries and duplicate delivery converge to one completed order and one stock deduction")
    @Test
    fun paymentCompletionIsRetryableAndIdempotent() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        val brand = createBrand("Nike")
        val product = createProduct(brand.brandId, "Pegasus", 129000L, 2)
        val checkout = checkoutOrder("consumer01", product.productId, 1)
        paymentGateway.failNextApproval()

        val failedPayment = pay("consumer01", checkout.body?.data?.orderId!!)
        val retriedPayment = pay("consumer01", checkout.body?.data?.orderId!!)
        val duplicatePayment = pay("consumer01", checkout.body?.data?.orderId!!)

        assertAll(
            { assertThat(failedPayment.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            { assertThat(retriedPayment.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(retriedPayment.body?.data?.status).isEqualTo(OrderStatus.COMPLETED) },
            { assertThat(duplicatePayment.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(duplicatePayment.body?.data?.status).isEqualTo(OrderStatus.COMPLETED) },
            { assertThat(duplicatePayment.body?.data?.paymentTransactionId).isEqualTo(retriedPayment.body?.data?.paymentTransactionId) },
            { assertThat(stockQuantity(product.productId)).isEqualTo(1) },
            {
                assertThat(stockReservationJpaRepository.findAllByOrderId(checkout.body?.data?.orderId!!)).allMatch {
                    it.status == StockReservationStatus.COMPLETED
                }
            },
        )
    }

    @DisplayName("A-06. Canceling before payment or expiring a reservation releases reserved stock without payment cancel")
    @Test
    fun cancelBeforePaymentAndReservationExpiryReleaseReservedStock() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        saveUser("consumer02")
        val brand = createBrand("Nike")
        val cancelProduct = createProduct(brand.brandId, "Pegasus", 129000L, 1)
        val expireProduct = createProduct(brand.brandId, "Dunk", 159000L, 1)
        val checkout = checkoutOrder("consumer01", cancelProduct.productId, 1)

        val canceled = cancel("consumer01", checkout.body?.data?.orderId!!)
        val checkoutAfterCancel = checkoutOrder("consumer02", cancelProduct.productId, 1)
        val expiringOrder = checkoutOrder("consumer01", expireProduct.productId, 1, LocalDateTime.now().minusMinutes(1))
        orderCheckoutFacade.expireReservations(OrderCommand.Expire(LocalDateTime.now()))
        val checkoutAfterExpiry = checkoutOrder("consumer02", expireProduct.productId, 1)

        assertAll(
            { assertThat(canceled.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(canceled.body?.data?.status).isEqualTo(OrderStatus.CANCELED) },
            { assertThat(canceled.body?.data?.cancelReason).isEqualTo(OrderCancelReason.USER_REQUESTED) },
            { assertThat(activeReservedQuantity(cancelProduct.productId)).isEqualTo(1) },
            { assertThat(checkoutAfterCancel.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(paymentGateway.canceledTransactionIds).isEmpty() },
            { assertThat(orderJpaRepository.findById(expiringOrder.body?.data?.orderId!!).orElseThrow().status).isEqualTo(OrderStatus.CANCELED) },
            { assertThat(checkoutAfterExpiry.statusCode).isEqualTo(HttpStatus.OK) },
        )
    }

    @DisplayName("A-07. Canceling a completed order restores stock and requests payment cancel")
    @Test
    fun cancelCompletedOrderRestoresStockAndCancelsPayment() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        val brand = createBrand("Nike")
        val product = createProduct(brand.brandId, "Pegasus", 129000L, 1)
        val checkout = checkoutOrder("consumer01", product.productId, 1)
        pay("consumer01", checkout.body?.data?.orderId!!)

        val canceled = cancel("consumer01", checkout.body?.data?.orderId!!)
        val paymentAfterCancel = pay("consumer01", checkout.body?.data?.orderId!!)

        assertAll(
            { assertThat(canceled.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(canceled.body?.data?.status).isEqualTo(OrderStatus.CANCELED) },
            { assertThat(stockQuantity(product.productId)).isEqualTo(1) },
            { assertThat(paymentGateway.canceledTransactionIds).containsExactly("payment-${checkout.body?.data?.orderId}") },
            { assertThat(paymentAfterCancel.statusCode).isEqualTo(HttpStatus.CONFLICT) },
            { assertThat(orderJpaRepository.findById(checkout.body?.data?.orderId!!).orElseThrow().status).isEqualTo(OrderStatus.CANCELED) },
        )
    }

    @DisplayName("A-08. Shipping-started orders cannot be canceled")
    @Test
    fun shippingStartedOrderCannotBeCanceled() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        val brand = createBrand("Nike")
        val product = createProduct(brand.brandId, "Pegasus", 129000L, 1)
        val checkout = checkoutOrder("consumer01", product.productId, 1)
        pay("consumer01", checkout.body?.data?.orderId!!)

        val shippingStarted = post<OrderV1Dto.OrderResponse>(
            "/api/v1/admin/orders/${checkout.body?.data?.orderId}/shipping-start",
            null,
            authHeaders("admin01"),
        )
        val cancelAfterShipping = cancel("consumer01", checkout.body?.data?.orderId!!)

        assertAll(
            { assertThat(shippingStarted.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(shippingStarted.body?.data?.status).isEqualTo(OrderStatus.SHIPPING_STARTED) },
            { assertThat(cancelAfterShipping.statusCode).isEqualTo(HttpStatus.CONFLICT) },
            { assertThat(orderJpaRepository.findById(checkout.body?.data?.orderId!!).orElseThrow().status).isEqualTo(OrderStatus.SHIPPING_STARTED) },
            { assertThat(stockQuantity(product.productId)).isEqualTo(0) },
            { assertThat(paymentGateway.canceledTransactionIds).isEmpty() },
        )
    }

    @DisplayName("A-09. Order snapshots are preserved after product and brand changes")
    @Test
    fun orderSnapshotIsPreservedAfterCatalogChanges() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        val brand = createBrand("Nike")
        val product = createProduct(brand.brandId, "Pegasus", 129000L, 2, listOf("https://cdn.example.com/old.png"))
        val checkout = checkoutOrder("consumer01", product.productId, 1)
        pay("consumer01", checkout.body?.data?.orderId!!)

        patch<CatalogV1Dto.BrandResponse>("/api/v1/admin/brands/${brand.brandId}", mapOf("name" to "Nike Updated"), authHeaders("admin01"))
        patch<CatalogV1Dto.ProductResponse>(
            "/api/v1/admin/products/${product.productId}",
            mapOf("name" to "Pegasus Updated", "price" to 99000L, "detailImageUrls" to listOf("https://cdn.example.com/new.png")),
            authHeaders("admin01"),
        )
        val orderDetail = get<OrderV1Dto.OrderResponse>("/api/v1/orders/${checkout.body?.data?.orderId}", authHeaders("consumer01"))
        val productDetail = get<CatalogV1Dto.ProductDetailResponse>("/api/v1/products/${product.productId}")

        assertAll(
            { assertThat(orderDetail.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(orderDetail.body?.data?.items?.single()?.productNameSnapshot).isEqualTo("Pegasus") },
            { assertThat(orderDetail.body?.data?.items?.single()?.brandNameSnapshot).isEqualTo("Nike") },
            { assertThat(orderDetail.body?.data?.items?.single()?.priceSnapshot).isEqualTo(129000L) },
            { assertThat(orderDetail.body?.data?.items?.single()?.quantity).isEqualTo(1) },
            { assertThat(productDetail.body?.data?.product?.productName).isEqualTo("Pegasus Updated") },
            { assertThat(productDetail.body?.data?.product?.brandName).isEqualTo("Nike Updated") },
            { assertThat(productDetail.body?.data?.product?.price).isEqualTo(99000L) },
            { assertThat(productDetail.body?.data?.detailImages).containsExactly("https://cdn.example.com/new.png") },
        )
    }

    @DisplayName("A-10. Concurrent checkout for one limited-stock item permits only one reservation")
    @Test
    fun limitedStockConcurrentCheckoutKeepsStockIntegrity() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        saveUser("consumer02")
        val brand = createBrand("Nike")
        val product = createProduct(brand.brandId, "Pegasus", 129000L, 1)
        addCartItem("consumer01", product.productId, 1)
        addCartItem("consumer02", product.productId, 1)

        val responses = runCartCheckoutsConcurrently("consumer01", "consumer02")
        val successfulOrder = responses.single { it.statusCode == HttpStatus.OK }.body?.data!!
        val successfulOrderOwnerId = orderJpaRepository.findById(successfulOrder.orderId).orElseThrow().userId
        val successfulOrderOwner = if (successfulOrderOwnerId == userId("consumer01")) "consumer01" else "consumer02"
        val payment = pay(successfulOrderOwner, successfulOrder.orderId)

        assertAll(
            { assertThat(responses.map { it.statusCode }).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT) },
            { assertThat(payment.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(stockQuantity(product.productId)).isEqualTo(0) },
            { assertThat(orderJpaRepository.count()).isEqualTo(1L) },
            { assertThat(productStockJpaRepository.findByProductIdAndDeletedAtIsNull(product.productId)?.stockQuantity).isNotNegative() },
        )
    }

    @DisplayName("A-11. Admin product order history shows completed and canceled consumer orders")
    @Test
    fun adminProductOrderHistoryMatchesConsumerOrderFlow() {
        saveUser("admin01", UserRole.ADMIN)
        saveUser("consumer01")
        saveUser("consumer02")
        val brand = createBrand("Nike")
        val product = createProduct(brand.brandId, "Pegasus", 129000L, 3)
        val firstOrder = checkoutOrder("consumer01", product.productId, 1)
        pay("consumer01", firstOrder.body?.data?.orderId!!)
        val secondOrder = checkoutOrder("consumer02", product.productId, 1)
        pay("consumer02", secondOrder.body?.data?.orderId!!)
        cancel("consumer02", secondOrder.body?.data?.orderId!!)

        val history = get<List<OrderV1Dto.OrderResponse>>(
            "/api/v1/admin/orders?productId=${product.productId}",
            authHeaders("admin01"),
        )

        assertAll(
            { assertThat(history.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(history.body?.data?.map { it.orderId }).contains(firstOrder.body?.data?.orderId, secondOrder.body?.data?.orderId) },
            {
                assertThat(history.body?.data?.associate { it.orderId to it.status })
                    .containsEntry(firstOrder.body?.data?.orderId, OrderStatus.COMPLETED)
                    .containsEntry(secondOrder.body?.data?.orderId, OrderStatus.CANCELED)
            },
        )
    }

    private fun saveUser(loginId: String, role: UserRole = UserRole.CONSUMER): User =
        userJpaRepository.save(
            User(
                loginId = loginId,
                encryptedPassword = passwordEncoder.encode(RawPassword(PASSWORD)),
                name = loginId,
                birthdate = LocalDate.of(1990, 1, 1),
                email = "$loginId@example.com",
                role = role,
            ),
        )

    private fun createBrand(name: String): CatalogV1Dto.BrandResponse =
        post<CatalogV1Dto.BrandResponse>("/api/v1/admin/brands", mapOf("name" to name), authHeaders("admin01"))
            .body?.data ?: error("Brand creation failed")

    private fun createProduct(
        brandId: Long,
        name: String,
        price: Long,
        stock: Int,
        detailImageUrls: List<String> = emptyList(),
    ): CatalogV1Dto.ProductResponse =
        post<CatalogV1Dto.ProductResponse>(
            "/api/v1/admin/products",
            mapOf(
                "brandId" to brandId,
                "name" to name,
                "price" to price,
                "initialStock" to stock,
                "detailImageUrls" to detailImageUrls,
            ),
            authHeaders("admin01"),
        ).body?.data ?: error("Product creation failed")

    private fun registerLike(loginId: String, productId: Long) {
        post<LikeV1Dto.LikeResponse>("/api/v1/products/$productId/likes", null, authHeaders(loginId))
    }

    private fun addCartItem(loginId: String, productId: Long, quantity: Int) {
        post<Unit>("/api/v1/cart/items", mapOf("productId" to productId, "quantity" to quantity), authHeaders(loginId))
    }

    private fun checkoutCart(
        loginId: String,
        productIdToAddFirst: Long? = null,
        expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10),
    ): ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> {
        productIdToAddFirst?.let { addCartItem(loginId, it, 1) }
        return post(
            "/api/v1/cart/checkout",
            mapOf(
                "deliveryAddress" to "Seoul",
                "deliveryRequest" to "Front door",
                "phoneNumber" to "010-1234-5678",
                "reservationExpiresAt" to expiresAt.toString(),
            ),
            authHeaders(loginId),
        )
    }

    private fun checkoutOrder(
        loginId: String,
        productId: Long,
        quantity: Int,
        expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10),
    ): ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> =
        post(
            "/api/v1/orders/checkout",
            mapOf(
                "items" to listOf(
                    mapOf(
                        "productId" to productId,
                        "productNameSnapshot" to "Pegasus",
                        "brandNameSnapshot" to "Nike",
                        "priceSnapshot" to 129000L,
                        "quantity" to quantity,
                    ),
                ),
                "deliveryAddress" to "Seoul",
                "deliveryRequest" to "Front door",
                "phoneNumber" to "010-1234-5678",
                "reservationExpiresAt" to expiresAt.toString(),
            ),
            authHeaders(loginId),
        )

    private fun pay(loginId: String, orderId: Long): ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> =
        post("/api/v1/orders/$orderId/payment", null, authHeaders(loginId))

    private fun cancel(loginId: String, orderId: Long): ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> =
        post("/api/v1/orders/$orderId/cancel", null, authHeaders(loginId))

    private fun runCartCheckoutsConcurrently(
        firstLoginId: String,
        secondLoginId: String,
    ): List<ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>>> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val responses = Collections.synchronizedList(mutableListOf<ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>>>())
        val failures = Collections.synchronizedList(mutableListOf<Throwable>())

        listOf(firstLoginId, secondLoginId).forEach { loginId ->
            executor.submit {
                try {
                    ready.countDown()
                    start.await(3, TimeUnit.SECONDS)
                    responses += checkoutCart(loginId)
                } catch (t: Throwable) {
                    failures += t
                } finally {
                    done.countDown()
                }
            }
        }

        ready.await(3, TimeUnit.SECONDS)
        start.countDown()
        done.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertThat(failures).isEmpty()
        return responses.toList()
    }

    private fun stockQuantity(productId: Long): Int =
        productStockJpaRepository.findByProductIdAndDeletedAtIsNull(productId)?.stockQuantity
            ?: error("Stock not found: $productId")

    private fun activeReservedQuantity(productId: Long): Int =
        stockReservationJpaRepository.sumQuantityByProductIdAndStatus(productId, StockReservationStatus.IN_PROGRESS).toInt()

    private fun productStats(productId: Long): ProductStats =
        productStatsJpaRepository.findByProductIdAndDeletedAtIsNull(productId)
            ?: error("Product stats not found: $productId")

    private fun userId(loginId: String): Long =
        userJpaRepository.findByLoginId(loginId)?.id ?: error("User not found: $loginId")

    private inline fun <reified T> get(
        path: String,
        headers: HttpHeaders = HttpHeaders(),
    ): ResponseEntity<ApiResponse<T>> =
        testRestTemplate.exchange(path, HttpMethod.GET, HttpEntity<Any>(headers), apiType())

    private inline fun <reified T> post(
        path: String,
        body: Any?,
        headers: HttpHeaders = HttpHeaders(),
    ): ResponseEntity<ApiResponse<T>> =
        testRestTemplate.exchange(path, HttpMethod.POST, HttpEntity(body, headers), apiType())

    private inline fun <reified T> patch(
        path: String,
        body: Any,
        headers: HttpHeaders = HttpHeaders(),
    ): ResponseEntity<ApiResponse<T>> =
        testRestTemplate.exchange(path, HttpMethod.PATCH, HttpEntity(body, headers), apiType())

    private inline fun <reified T> delete(
        path: String,
        headers: HttpHeaders = HttpHeaders(),
    ): ResponseEntity<ApiResponse<T>> =
        testRestTemplate.exchange(path, HttpMethod.DELETE, HttpEntity<Any>(headers), apiType())

    private inline fun <reified T> apiType(): ParameterizedTypeReference<ApiResponse<T>> =
        object : ParameterizedTypeReference<ApiResponse<T>>() {}

    private fun authHeaders(loginId: String): HttpHeaders = HttpHeaders().apply {
        add("X-Loopers-LoginId", loginId)
        add("X-Loopers-LoginPw", PASSWORD)
    }

    data class BrandDetailResponse(
        val brandId: Long,
        val name: String,
        val products: List<CatalogV1Dto.ProductDisplayResponse>,
    )

    companion object {
        private const val PASSWORD = "abcd1234"
    }
}
