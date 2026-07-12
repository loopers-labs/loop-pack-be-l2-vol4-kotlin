package com.loopers.interfaces.api.order

import com.loopers.domain.coupon.enums.CouponIssueStatus
import com.loopers.domain.coupon.enums.DiscountType
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.waitingqueue.EntryTokenRepository
import com.loopers.domain.user.PasswordEncoder
import com.loopers.infrastructure.brand.entity.BrandEntity
import com.loopers.infrastructure.brand.repository.BrandJpaRepository
import com.loopers.infrastructure.coupon.entity.CouponEntity
import com.loopers.infrastructure.coupon.entity.CouponIssueEntity
import com.loopers.infrastructure.coupon.repository.CouponIssueJpaRepository
import com.loopers.infrastructure.coupon.repository.CouponJpaRepository
import com.loopers.infrastructure.inventory.entity.InventoryEntity
import com.loopers.infrastructure.inventory.repository.InventoryJpaRepository
import com.loopers.infrastructure.member.entity.MemberEntity
import com.loopers.infrastructure.member.repository.MemberJpaRepository
import com.loopers.infrastructure.order.entity.OrderEntity
import com.loopers.infrastructure.order.entity.OrderItemEntity
import com.loopers.infrastructure.order.repository.OrderJpaRepository
import com.loopers.infrastructure.product.entity.ProductEntity
import com.loopers.infrastructure.product.repository.ProductJpaRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.order.dto.OrderV1Dto
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val memberJpaRepository: MemberJpaRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val inventoryJpaRepository: InventoryJpaRepository,
    private val couponJpaRepository: CouponJpaRepository,
    private val couponIssueJpaRepository: CouponIssueJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val entryTokenRepository: EntryTokenRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    inner class PlaceOrder {
        @DisplayName("여러 상품을 주문하면 주문을 저장하고 재고를 차감한다")
        @Test
        fun placesOrder() {
            val member = createMember()
            val brand = createBrand()
            val firstProduct = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            val secondProduct = createProduct(brandId = brand.id, name = "cap", price = 5_000L)
            createInventory(productId = firstProduct.id, quantity = 10L)
            createInventory(productId = secondProduct.id, quantity = 3L)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(
                            OrderV1Dto.CreateOrderRequest.Item(productId = firstProduct.id, quantity = 2L),
                            OrderV1Dto.CreateOrderRequest.Item(productId = secondProduct.id, quantity = 1L),
                        ),
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            val orders = orderJpaRepository.findAll()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.memberId).isEqualTo(member.id) },
                { assertThat(response.body?.data?.status).isEqualTo(OrderStatus.PENDING_PAYMENT) },
                { assertThat(response.body?.data?.totalAmount).isEqualTo(25_000L) },
                { assertThat(response.body?.data?.items).hasSize(2) },
                { assertThat(orders).hasSize(1) },
                { assertThat(countOrderItems()).isEqualTo(2) },
                { assertThat(inventoryJpaRepository.findByProductId(firstProduct.id)?.quantity).isEqualTo(8L) },
                { assertThat(inventoryJpaRepository.findByProductId(secondProduct.id)?.quantity).isEqualTo(2L) },
            )
        }

        @DisplayName("입장 토큰이 없으면 주문할 수 없다")
        @Test
        fun returnsUnauthorized_whenEntryTokenIsMissing() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            createInventory(productId = product.id, quantity = 1L)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 1L)),
                    ),
                    createAuthHeaders(entryToken = null),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(orderJpaRepository.findAll()).isEmpty() },
                { assertThat(inventoryJpaRepository.findByProductId(product.id)?.quantity).isEqualTo(1L) },
            )
        }

        @DisplayName("주문에 성공하면 사용한 입장 토큰을 삭제한다")
        @Test
        fun deletesEntryToken_whenOrderSucceeds() {
            val member = createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            createInventory(productId = product.id, quantity = 1L)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 1L)),
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(entryTokenRepository.find(member.id)).isNull() },
            )
        }

        @DisplayName("사용 가능한 정액 쿠폰을 적용하면 할인 금액을 반영하고 쿠폰을 사용 처리한다")
        @Test
        fun placesOrderWithFixedCoupon() {
            val member = createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            createInventory(productId = product.id, quantity = 10L)
            val coupon = createCoupon(
                type = DiscountType.FIXED,
                discountValue = 3_000L,
                minOrderAmount = 10_000L,
            )
            val couponIssue = createCouponIssue(memberId = member.id, coupon = coupon)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 2L)),
                        couponId = couponIssue.id,
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            val savedOrder = orderJpaRepository.findAll().single()
            val usedCoupon = couponIssueJpaRepository.findById(couponIssue.id).orElseThrow()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.originalAmount).isEqualTo(20_000L) },
                { assertThat(response.body?.data?.discountAmount).isEqualTo(3_000L) },
                { assertThat(response.body?.data?.totalAmount).isEqualTo(17_000L) },
                { assertThat(savedOrder.originalAmount).isEqualTo(20_000L) },
                { assertThat(savedOrder.discountAmount).isEqualTo(3_000L) },
                { assertThat(savedOrder.totalAmount).isEqualTo(17_000L) },
                { assertThat(savedOrder.couponIssueId).isEqualTo(couponIssue.id) },
                { assertThat(usedCoupon.status).isEqualTo(CouponIssueStatus.USED) },
                { assertThat(usedCoupon.usedAt).isNotNull() },
                { assertThat(inventoryJpaRepository.findByProductId(product.id)?.quantity).isEqualTo(8L) },
            )
        }

        @DisplayName("사용 가능한 정률 쿠폰을 적용하면 할인 금액을 반영하고 쿠폰을 사용 처리한다")
        @Test
        fun placesOrderWithRateCoupon() {
            val member = createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            createInventory(productId = product.id, quantity = 10L)
            val coupon = createCoupon(
                type = DiscountType.RATE,
                discountValue = 10L,
                minOrderAmount = 10_000L,
            )
            val couponIssue = createCouponIssue(memberId = member.id, coupon = coupon)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 2L)),
                        couponId = couponIssue.id,
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            val savedOrder = orderJpaRepository.findAll().single()
            val usedCoupon = couponIssueJpaRepository.findById(couponIssue.id).orElseThrow()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.originalAmount).isEqualTo(20_000L) },
                { assertThat(response.body?.data?.discountAmount).isEqualTo(2_000L) },
                { assertThat(response.body?.data?.totalAmount).isEqualTo(18_000L) },
                { assertThat(savedOrder.discountAmount).isEqualTo(2_000L) },
                { assertThat(savedOrder.totalAmount).isEqualTo(18_000L) },
                { assertThat(savedOrder.couponIssueId).isEqualTo(couponIssue.id) },
                { assertThat(usedCoupon.status).isEqualTo(CouponIssueStatus.USED) },
                { assertThat(usedCoupon.usedAt).isNotNull() },
                { assertThat(inventoryJpaRepository.findByProductId(product.id)?.quantity).isEqualTo(8L) },
            )
        }

        @DisplayName("이미 사용된 쿠폰을 적용하면 주문을 저장하지 않고 재고를 차감하지 않는다")
        @Test
        fun returnsBadRequest_whenCouponIssueIsAlreadyUsed() {
            val member = createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            createInventory(productId = product.id, quantity = 10L)
            val coupon = createCoupon(
                type = DiscountType.FIXED,
                discountValue = 3_000L,
                minOrderAmount = 10_000L,
            )
            val couponIssue = createCouponIssue(
                memberId = member.id,
                coupon = coupon,
                status = CouponIssueStatus.USED,
                usedAt = ZonedDateTime.now().minusDays(1),
            )

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 2L)),
                        couponId = couponIssue.id,
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            val unchangedCoupon = couponIssueJpaRepository.findById(couponIssue.id).orElseThrow()
            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(orderJpaRepository.findAll()).isEmpty() },
                { assertThat(inventoryJpaRepository.findByProductId(product.id)?.quantity).isEqualTo(10L) },
                { assertThat(unchangedCoupon.status).isEqualTo(CouponIssueStatus.USED) },
                { assertThat(unchangedCoupon.usedAt).isEqualTo(couponIssue.usedAt) },
            )
        }

        @DisplayName("존재하지 않는 쿠폰을 적용하면 주문을 저장하지 않고 재고를 차감하지 않는다")
        @Test
        fun returnsNotFound_whenCouponIssueDoesNotExist() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            createInventory(productId = product.id, quantity = 10L)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 2L)),
                        couponId = 999L,
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(orderJpaRepository.findAll()).isEmpty() },
                { assertThat(inventoryJpaRepository.findByProductId(product.id)?.quantity).isEqualTo(10L) },
            )
        }

        @DisplayName("다른 회원의 쿠폰을 적용하면 주문을 저장하지 않고 재고를 차감하지 않는다")
        @Test
        fun returnsBadRequest_whenCouponIssueBelongsToOtherMember() {
            createMember()
            val otherMember = createMember(loginId = "other123")
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            createInventory(productId = product.id, quantity = 10L)
            val coupon = createCoupon(
                type = DiscountType.FIXED,
                discountValue = 3_000L,
                minOrderAmount = 10_000L,
            )
            val couponIssue = createCouponIssue(memberId = otherMember.id, coupon = coupon)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 2L)),
                        couponId = couponIssue.id,
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertFailedCouponOrderDidNotMutate(
                response = response,
                productId = product.id,
                couponIssue = couponIssue,
            )
        }

        @DisplayName("만료된 쿠폰을 적용하면 주문을 저장하지 않고 재고를 차감하지 않는다")
        @Test
        fun returnsBadRequest_whenCouponIssueIsExpired() {
            val member = createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            createInventory(productId = product.id, quantity = 10L)
            val coupon = createCoupon(
                type = DiscountType.FIXED,
                discountValue = 3_000L,
                minOrderAmount = 10_000L,
                expiredAt = ZonedDateTime.now().minusDays(1),
            )
            val couponIssue = createCouponIssue(memberId = member.id, coupon = coupon)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 2L)),
                        couponId = couponIssue.id,
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertFailedCouponOrderDidNotMutate(
                response = response,
                productId = product.id,
                couponIssue = couponIssue,
            )
        }

        @DisplayName("최소 주문 금액을 만족하지 못한 쿠폰을 적용하면 주문을 저장하지 않고 재고를 차감하지 않는다")
        @Test
        fun returnsBadRequest_whenOrderAmountIsLessThanCouponMinimum() {
            val member = createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            createInventory(productId = product.id, quantity = 10L)
            val coupon = createCoupon(
                type = DiscountType.FIXED,
                discountValue = 3_000L,
                minOrderAmount = 30_000L,
            )
            val couponIssue = createCouponIssue(memberId = member.id, coupon = coupon)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 2L)),
                        couponId = couponIssue.id,
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertFailedCouponOrderDidNotMutate(
                response = response,
                productId = product.id,
                couponIssue = couponIssue,
            )
        }

        @DisplayName("동일한 쿠폰으로 동시에 주문해도 쿠폰은 한 번만 사용된다")
        @Test
        fun usesCouponIssueOnlyOnce_whenOrdersAreConcurrent() {
            val member = createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            createInventory(productId = product.id, quantity = 20L)
            val coupon = createCoupon(
                type = DiscountType.FIXED,
                discountValue = 1_000L,
                minOrderAmount = 10_000L,
            )
            val couponIssue = createCouponIssue(memberId = member.id, coupon = coupon)
            val executor = Executors.newFixedThreadPool(CONCURRENT_ORDER_COUNT)
            val startLatch = java.util.concurrent.CountDownLatch(1)

            val futures = (1..CONCURRENT_ORDER_COUNT).map {
                executor.submit(
                    Callable {
                    startLatch.await()
                    testRestTemplate.exchange(
                        ORDERS_ENDPOINT,
                        HttpMethod.POST,
                        HttpEntity(
                            OrderV1Dto.CreateOrderRequest(
                                items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 1L)),
                                couponId = couponIssue.id,
                            ),
                            createAuthHeaders(),
                        ),
                        object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
                    )
                    },
                )
            }

            startLatch.countDown()
            val responses = futures.map { it.get(10, TimeUnit.SECONDS) }
            executor.shutdown()

            val usedCoupon = couponIssueJpaRepository.findById(couponIssue.id).orElseThrow()
            assertAll(
                { assertThat(responses.count { it.statusCode == HttpStatus.OK }).isEqualTo(1) },
                { assertThat(responses.count { it.statusCode == HttpStatus.BAD_REQUEST }).isEqualTo(CONCURRENT_ORDER_COUNT - 1) },
                { assertThat(orderJpaRepository.findAll()).hasSize(1) },
                { assertThat(inventoryJpaRepository.findByProductId(product.id)?.quantity).isEqualTo(19L) },
                { assertThat(usedCoupon.status).isEqualTo(CouponIssueStatus.USED) },
                { assertThat(usedCoupon.usedAt).isNotNull() },
            )
        }

        @DisplayName("동일한 상품에 대해 여러 주문이 동시에 요청되어도 재고는 성공한 주문 수만큼만 차감된다")
        @Test
        fun deductsInventoryOnlyForSuccessfulOrders_whenOrdersAreConcurrent() {
            val members = (1..CONCURRENT_ORDER_COUNT).map { index ->
                createMember(loginId = "loopers$index")
            }
            val brand = createBrand()
            val product = createProduct(brandId = brand.id, name = "hoodie", price = 10_000L)
            createInventory(productId = product.id, quantity = CONCURRENT_ORDER_STOCK)
            val executor = Executors.newFixedThreadPool(CONCURRENT_ORDER_COUNT)
            val startLatch = java.util.concurrent.CountDownLatch(1)

            val futures = members.map { member ->
                executor.submit(
                    Callable {
                        startLatch.await()
                        testRestTemplate.exchange(
                            ORDERS_ENDPOINT,
                            HttpMethod.POST,
                            HttpEntity(
                                OrderV1Dto.CreateOrderRequest(
                                    items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 1L)),
                                ),
                                createAuthHeaders(loginId = member.loginId),
                            ),
                            object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
                        )
                    },
                )
            }

            startLatch.countDown()
            val responses = futures.map { it.get(10, TimeUnit.SECONDS) }
            executor.shutdown()

            assertAll(
                { assertThat(responses.count { it.statusCode == HttpStatus.OK }).isEqualTo(CONCURRENT_ORDER_STOCK.toInt()) },
                { assertThat(responses.count { it.statusCode == HttpStatus.CONFLICT }).isEqualTo(CONCURRENT_ORDER_COUNT - CONCURRENT_ORDER_STOCK.toInt()) },
                { assertThat(orderJpaRepository.findAll()).hasSize(CONCURRENT_ORDER_STOCK.toInt()) },
                { assertThat(inventoryJpaRepository.findByProductId(product.id)?.quantity).isEqualTo(0L) },
            )
        }

        @DisplayName("재고가 부족하면 주문을 저장하지 않고 재고를 차감하지 않는다")
        @Test
        fun returnsConflict_whenInventoryIsInsufficient() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            createInventory(productId = product.id, quantity = 1L)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 2L)),
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(orderJpaRepository.findAll()).isEmpty() },
                { assertThat(inventoryJpaRepository.findByProductId(product.id)?.quantity).isEqualTo(1L) },
            )
        }

        @DisplayName("존재하지 않는 상품은 주문할 수 없다")
        @Test
        fun returnsNotFound_whenProductDoesNotExist() {
            createMember()

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = 999L, quantity = 1L)),
                    ),
                    createAuthHeaders(),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @DisplayName("인증 정보가 올바르지 않으면 주문할 수 없다")
        @Test
        fun returnsUnauthorized_whenCredentialsAreInvalid() {
            createMember()
            val brand = createBrand()
            val product = createProduct(brandId = brand.id)
            createInventory(productId = product.id, quantity = 1L)

            val response = testRestTemplate.exchange(
                ORDERS_ENDPOINT,
                HttpMethod.POST,
                HttpEntity(
                    OrderV1Dto.CreateOrderRequest(
                        items = listOf(OrderV1Dto.CreateOrderRequest.Item(productId = product.id, quantity = 1L)),
                    ),
                    createAuthHeaders(password = "Wrong123!"),
                ),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    inner class GetOrders {
        @DisplayName("로그인한 회원의 기간 내 주문 목록을 조회한다")
        @Test
        fun getsOrders() {
            val member = createMember()
            val otherMember = createMember(loginId = "other123")
            val now = ZonedDateTime.now()
            val firstOrder = createOrder(memberId = member.id, orderedAt = now.minusDays(1), totalAmount = 10_000L)
            val secondOrder = createOrder(memberId = member.id, orderedAt = now, totalAmount = 20_000L)
            createOrder(memberId = otherMember.id, orderedAt = now, totalAmount = 30_000L)

            val response = testRestTemplate.exchange(
                ordersUrl(startAt = now.minusDays(2).toLocalDate(), endAt = now.plusDays(1).toLocalDate()),
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderSummaryResponse>>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.map { it.orderId }).containsExactly(secondOrder.id, firstOrder.id) },
                { assertThat(response.body?.data?.map { it.totalAmount }).containsExactly(20_000L, 10_000L) },
            )
        }

        @DisplayName("인증 정보가 올바르지 않으면 주문 목록을 조회할 수 없다")
        @Test
        fun returnsUnauthorized_whenCredentialsAreInvalid() {
            createMember()
            val now = ZonedDateTime.now()

            val response = testRestTemplate.exchange(
                ordersUrl(startAt = now.minusDays(1).toLocalDate(), endAt = now.plusDays(1).toLocalDate()),
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders(password = "Wrong123!")),
                object : ParameterizedTypeReference<ApiResponse<List<OrderV1Dto.OrderSummaryResponse>>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    inner class GetOrder {
        @DisplayName("로그인한 회원의 주문 상세를 조회한다")
        @Test
        fun getsOrder() {
            val member = createMember()
            val order = createOrder(memberId = member.id)

            val response = testRestTemplate.exchange(
                "$ORDERS_ENDPOINT/${order.id}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.orderId).isEqualTo(order.id) },
                { assertThat(response.body?.data?.items).hasSize(1) },
                { assertThat(response.body?.data?.items?.single()?.productName).isEqualTo("hoodie") },
            )
        }

        @DisplayName("다른 회원의 주문은 조회할 수 없다")
        @Test
        fun returnsNotFound_whenOrderBelongsToOtherMember() {
            createMember()
            val otherMember = createMember(loginId = "other123")
            val order = createOrder(memberId = otherMember.id)

            val response = testRestTemplate.exchange(
                "$ORDERS_ENDPOINT/${order.id}",
                HttpMethod.GET,
                HttpEntity<Unit>(createAuthHeaders()),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {},
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
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
        name: String = "loopers hoodie",
        price: Long = 10_000L,
    ): ProductEntity {
        return productJpaRepository.save(
            ProductEntity(
                brandId = brandId,
                name = name,
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

    private fun createCoupon(
        type: DiscountType,
        discountValue: Long,
        minOrderAmount: Long? = null,
        expiredAt: ZonedDateTime = ZonedDateTime.now().plusDays(30),
    ): CouponEntity {
        return couponJpaRepository.save(
            CouponEntity(
                name = "coupon-${System.nanoTime()}",
                type = type,
                discountValue = discountValue,
                minOrderAmount = minOrderAmount,
                expiredAt = expiredAt,
                isDeleted = false,
            ),
        )
    }

    private fun createCouponIssue(
        memberId: Long,
        coupon: CouponEntity,
        status: CouponIssueStatus = CouponIssueStatus.AVAILABLE,
        usedAt: ZonedDateTime? = null,
    ): CouponIssueEntity {
        return couponIssueJpaRepository.save(
            CouponIssueEntity(
                memberId = memberId,
                couponId = coupon.id,
                status = status,
                type = coupon.type,
                discountValue = coupon.discountValue,
                minOrderAmount = coupon.minOrderAmount,
                expiredAt = coupon.expiredAt,
                usedAt = usedAt,
            ),
        )
    }

    private fun createOrder(
        memberId: Long,
        orderedAt: ZonedDateTime = ZonedDateTime.now(),
        totalAmount: Long = 10_000L,
    ): OrderEntity {
        val order = OrderEntity(
            orderNumber = "order-$memberId-${orderedAt.toInstant().toEpochMilli()}-$totalAmount",
            memberId = memberId,
            status = OrderStatus.COMPLETED,
            totalAmount = totalAmount,
            originalAmount = totalAmount,
            discountAmount = 0L,
            couponIssueId = null,
            orderedAt = orderedAt,
        )
        order.addItem(
            OrderItemEntity(
                productId = 1L,
                productName = "hoodie",
                brandName = "loopers",
                unitPrice = totalAmount,
                quantity = 1L,
                totalAmount = totalAmount,
            ),
        )

        return orderJpaRepository.save(order)
    }

    private fun createAuthHeaders(
        loginId: String = LOGIN_ID,
        password: String = RAW_PASSWORD,
        entryToken: String? = "entry-token-$loginId",
    ): HttpHeaders {
        entryToken
            ?.let { token ->
                memberJpaRepository.findByLoginId(loginId)
                    ?.let { member ->
                        entryTokenRepository.issue(memberId = member.id, token = token, ttl = Duration.ofMinutes(5))
                    }
            }

        return HttpHeaders().apply {
            set("X-Loopers-LoginId", loginId)
            set("X-Loopers-LoginPw", password)
            entryToken?.let { token -> set("X-Entry-Token", token) }
        }
    }

    private fun countOrderItems(): Long {
        return jdbcTemplate.queryForObject("select count(*) from order_item", Long::class.java) ?: 0L
    }

    private fun assertFailedCouponOrderDidNotMutate(
        response: org.springframework.http.ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>>,
        productId: Long,
        couponIssue: CouponIssueEntity,
    ) {
        val unchangedCoupon = couponIssueJpaRepository.findById(couponIssue.id).orElseThrow()
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
            { assertThat(orderJpaRepository.findAll()).isEmpty() },
            { assertThat(inventoryJpaRepository.findByProductId(productId)?.quantity).isEqualTo(10L) },
            { assertThat(unchangedCoupon.status).isEqualTo(couponIssue.status) },
            { assertThat(unchangedCoupon.usedAt).isEqualTo(couponIssue.usedAt) },
        )
    }

    private fun ordersUrl(startAt: LocalDate, endAt: LocalDate): String {
        return "$ORDERS_ENDPOINT?startAt=$startAt&endAt=$endAt"
    }

    private companion object {
        private val redisContainer = GenericContainer(DockerImageName.parse("redis:latest"))
            .withExposedPorts(REDIS_PORT)
            .apply {
                start()
            }

        private const val ORDERS_ENDPOINT = "/api/v1/orders"
        private const val LOGIN_ID = "loopers123"
        private const val RAW_PASSWORD = "Loopers123!"
        private const val CONCURRENT_ORDER_COUNT = 10
        private const val CONCURRENT_ORDER_STOCK = 5L
        private const val REDIS_PORT = 6379

        @JvmStatic
        @DynamicPropertySource
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("datasource.redis.database") { "0" }
            registry.add("datasource.redis.master.host") { redisContainer.host }
            registry.add("datasource.redis.master.port") { redisContainer.getMappedPort(REDIS_PORT).toString() }
            registry.add("datasource.redis.replicas[0].host") { redisContainer.host }
            registry.add("datasource.redis.replicas[0].port") { redisContainer.getMappedPort(REDIS_PORT).toString() }
        }
    }
}
