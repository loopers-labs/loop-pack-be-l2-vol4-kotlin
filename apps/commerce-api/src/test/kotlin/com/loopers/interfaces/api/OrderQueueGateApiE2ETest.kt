package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.order.infrastructure.persistence.OrderJpaRepository
import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.infrastructure.persistence.stock.ProductStockJpaRepository
import com.loopers.domain.product.model.ProductSaleType
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.기본_로그인_ID
import com.loopers.domain.user.support.UserSteps.Companion.기본_비밀번호
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.domain.waitingqueue.application.WaitingQueueFacade
import com.loopers.domain.waitingqueue.config.WaitingQueueProperties
import com.loopers.domain.waitingqueue.infrastructure.redis.constant.WaitingQueueRedisConstants
import com.loopers.support.outbox.event.CommerceOutboxEventType
import com.loopers.support.outbox.persistence.OutboxEventJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@TestPropertySource(
    properties = [
        "commerce.waiting-queue.token-ttl=PT1S",
        "commerce.waiting-queue.scheduler-delay=PT10S",
        "commerce.waiting-queue.admission-batch-size=1",
        "commerce.waiting-queue.scheduler-jitter-max=PT0S",
        "commerce.waiting-queue.polling-interval=PT2S",
        "commerce.waiting-queue.redis-key-prefix=waiting-queue",
    ],
)
class OrderQueueGateApiE2ETest
    @Autowired
    constructor(
        private val userService: UserService,
        private val brandService: BrandService,
        private val productFacade: ProductFacade,
        private val productStockJpaRepository: ProductStockJpaRepository,
        private val orderJpaRepository: OrderJpaRepository,
        private val outboxEventJpaRepository: OutboxEventJpaRepository,
        private val waitingQueueFacade: WaitingQueueFacade,
        private val waitingQueueProperties: WaitingQueueProperties,
        private val redissonClient: RedissonClient,
        private val transactionTemplate: TransactionTemplate,
    ) : ApiTest() {
        private val mapResponseType =
            object : ParameterizedTypeReference<ApiResponse<Map<String, Any?>>>() {}

        @Test
        fun `주문은_X_Queue_Token_누락시_재고와_주문_변경_전에_거부된다`() {
            userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)

            val response = placeOrder(
                productId = productId,
                quantity = 1,
                headers = authHeaders(idempotencyKey = "missing-token-key"),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(5)
            assertThat(outboxEventJpaRepository.count()).isZero()
        }

        @Test
        fun `입장_토큰이_유효해도_Idempotency_Key가_없으면_주문_mutation_전에_거부된다`() {
            val user = userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)
            val token = admittedToken(user.id)

            val response = placeOrder(
                productId = productId,
                quantity = 1,
                headers = authHeaders(queueToken = token, idempotencyKey = null),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(orderJpaRepository.count()).isZero()
            assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(5)
            assertThat(outboxEventJpaRepository.count()).isZero()
        }

        @Test
        fun `일반_상품_주문은_대기열_토큰과_Idempotency_Key_없이_기존_주문_흐름을_사용한다`() {
            userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5, requiresWaitingQueue = false)

            val response = placeOrder(
                productId = productId,
                quantity = 1,
                headers = authHeaders(queueToken = null, idempotencyKey = null),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(orderJpaRepository.count()).isEqualTo(1L)
            assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(4)
            assertThat(
                outboxEventJpaRepository.findAll().count {
                    it.type == CommerceOutboxEventType.ORDER_CREATED_V1.name
                },
            ).isEqualTo(1)
        }

        @Test
        fun `일반_상품과_선착순_상품을_함께_주문하면_주문_전체에_대기열_관문을_적용한다`() {
            userService.signUp(사용자_회원가입())
            val normalProductId = registerProduct(
                price = 10_000,
                initialStock = 5,
                requiresWaitingQueue = false,
            )
            val limitedProductId = registerProduct(price = 20_000, initialStock = 3)

            val response = placeOrder(
                items = listOf(normalProductId to 1L, limitedProductId to 1L),
                headers = authHeaders(queueToken = null, idempotencyKey = "mixed-order-key"),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(orderJpaRepository.count()).isZero()
            assertThat(productStockJpaRepository.findById(normalProductId).orElseThrow().leftStock).isEqualTo(5)
            assertThat(productStockJpaRepository.findById(limitedProductId).orElseThrow().leftStock).isEqualTo(3)
            assertThat(outboxEventJpaRepository.count()).isZero()
        }

        @Test
        fun `주문은_유효하지_않은_X_Queue_Token이면_재고와_주문_변경_전에_거부된다`() {
            userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)

            val response = placeOrder(
                productId = productId,
                quantity = 1,
                headers = authHeaders(queueToken = "invalid-token", idempotencyKey = "invalid-token-key"),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(5)
        }

        @Test
        fun `주문은_만료된_X_Queue_Token이면_재고와_주문_변경_전에_거부된다`() {
            val user = userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)
            val token = admittedToken(user.id)
            Thread.sleep(1_200)

            val response = placeOrder(
                productId = productId,
                quantity = 1,
                headers = authHeaders(queueToken = token, idempotencyKey = "expired-token-key"),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(5)
        }

        @Test
        fun `주문은_availableAt_이전_X_Queue_Token이면_CONFLICT로_재고와_주문_변경_전에_거부된다`() {
            val user = userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)
            val token = admittedToken(user.id)
            val futureAvailableAt = Instant.now().plusSeconds(60)
            tokenState(token)[WaitingQueueRedisConstants.AVAILABLE_AT_FIELD] =
                futureAvailableAt.toEpochMilli().toString()

            val response = placeOrder(
                productId = productId,
                quantity = 1,
                headers = authHeaders(queueToken = token, idempotencyKey = "not-yet-available-token-key"),
            )
            val tokenAfterAttempt = tokenState(token)

            assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(orderJpaRepository.count()).isEqualTo(0L)
            assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(5)
            assertThat(tokenAfterAttempt[WaitingQueueRedisConstants.STATUS_FIELD])
                .isEqualTo(WaitingQueueRedisConstants.ACTIVE_STATUS)
        }

        @Test
        fun `유효한_토큰은_주문을_허용하고_성공_후_소모되어_다른_멱등키_재사용은_거부된다`() {
            val user = userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)
            val token = admittedToken(user.id)

            val first = placeOrder(
                productId = productId,
                quantity = 1,
                headers = authHeaders(queueToken = token, idempotencyKey = "queue-success-key"),
            )
            val second = placeOrder(
                productId = productId,
                quantity = 1,
                headers = authHeaders(queueToken = token, idempotencyKey = "different-key"),
            )

            assertThat(first.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(second.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
            assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(4)
            assertThat(
                outboxEventJpaRepository.findAll().count {
                    it.type == CommerceOutboxEventType.ORDER_CREATED_V1.name
                },
            ).isEqualTo(1)
        }

        @Test
        fun `같은_입장_토큰의_서로_다른_멱등키_동시_주문은_하나만_주문_mutation에_진입한다`() {
            val user = userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)
            val token = admittedToken(user.id)
            val executor = Executors.newFixedThreadPool(2)
            val idempotencyKeys = listOf("queue-race-key-1", "queue-race-key-2")
            lateinit var futures: List<Future<ResponseEntity<ApiResponse<Map<String, Any?>>>>>

            try {
                transactionTemplate.executeWithoutResult {
                    productStockJpaRepository.findByProductIdsForUpdate(listOf(productId))
                    val ready = CountDownLatch(2)
                    val start = CountDownLatch(1)
                    futures = idempotencyKeys.map { idempotencyKey ->
                        executor.submit<ResponseEntity<ApiResponse<Map<String, Any?>>>> {
                            ready.countDown()
                            start.await()
                            placeOrder(
                                productId = productId,
                                quantity = 1,
                                headers = authHeaders(queueToken = token, idempotencyKey = idempotencyKey),
                            )
                        }
                    }
                    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
                    start.countDown()
                    Thread.sleep(300)
                }

                val responses = idempotencyKeys.zip(futures.map { it.get(10, TimeUnit.SECONDS) }).toMap()
                val statuses = responses.values.map { it.statusCode }

                assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.UNAUTHORIZED)
                val successfulIdempotencyKey = responses.entries
                    .single { it.value.statusCode == HttpStatus.CREATED }
                    .key
                val consumedToken = tokenState(token)

                assertThat(orderJpaRepository.count()).isEqualTo(1L)
                assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(4)
                assertThat(consumedToken[WaitingQueueRedisConstants.STATUS_FIELD])
                    .isEqualTo(WaitingQueueRedisConstants.CONSUMED_STATUS)
                assertThat(consumedToken[WaitingQueueRedisConstants.IDEMPOTENCY_KEY_FIELD])
                    .isEqualTo(successfulIdempotencyKey)
                assertThat(userAdmissionState(user.id).isExists).isFalse()
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `같은_입장_토큰과_같은_멱등키의_동시_주문은_처리중_요청을_중복_mutation에_진입시키지_않는다`() {
            val user = userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)
            val token = admittedToken(user.id)
            val executor = Executors.newFixedThreadPool(2)
            lateinit var futures: List<Future<ResponseEntity<ApiResponse<Map<String, Any?>>>>>

            try {
                transactionTemplate.executeWithoutResult {
                    productStockJpaRepository.findByProductIdsForUpdate(listOf(productId))
                    val ready = CountDownLatch(2)
                    val start = CountDownLatch(1)
                    futures = (1..2).map {
                        executor.submit<ResponseEntity<ApiResponse<Map<String, Any?>>>> {
                            ready.countDown()
                            start.await()
                            placeOrder(
                                productId = productId,
                                quantity = 1,
                                headers = authHeaders(queueToken = token, idempotencyKey = "same-concurrent-key"),
                            )
                        }
                    }
                    assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
                    start.countDown()
                    Thread.sleep(300)
                }

                val statuses = futures.map { it.get(10, TimeUnit.SECONDS).statusCode }

                assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.CREATED, HttpStatus.CONFLICT)
                assertThat(orderJpaRepository.count()).isEqualTo(1L)
                assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(4)
                assertThat(tokenState(token)[WaitingQueueRedisConstants.STATUS_FIELD])
                    .isEqualTo(WaitingQueueRedisConstants.CONSUMED_STATUS)
            } finally {
                executor.shutdownNow()
            }
        }

        @Test
        fun `재고_부족으로_주문이_실패하면_토큰은_소모되지_않고_TTL까지_다시_사용할_수_있다`() {
            val user = userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 1)
            val token = admittedToken(user.id)

            val failed = placeOrder(
                productId = productId,
                quantity = 2,
                headers = authHeaders(queueToken = token, idempotencyKey = "stock-failure-key"),
            )
            val retry = placeOrder(
                productId = productId,
                quantity = 1,
                headers = authHeaders(queueToken = token, idempotencyKey = "stock-failure-retry-key"),
            )

            assertThat(failed.statusCode).isEqualTo(HttpStatus.CONFLICT)
            assertThat(retry.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(0)
        }

        @Test
        fun `소모된_토큰도_같은_멱등키_재시도는_기존_주문_멱등성_경로로_통과한다`() {
            val user = userService.signUp(사용자_회원가입())
            val productId = registerProduct(price = 10_000, initialStock = 5)
            val token = admittedToken(user.id)
            val headers = authHeaders(queueToken = token, idempotencyKey = "same-idempotency-key")
            val first = placeOrder(productId = productId, quantity = 1, headers = headers)

            val retry = placeOrder(productId = productId, quantity = 1, headers = headers)

            assertThat(first.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(retry.statusCode).isEqualTo(HttpStatus.CREATED)
            assertThat(retry.body?.data?.number("id")).isEqualTo(first.body?.data?.number("id"))
            assertThat(productStockJpaRepository.findById(productId).orElseThrow().leftStock).isEqualTo(4)
        }

        private fun registerProduct(
            price: Long,
            initialStock: Long,
            requiresWaitingQueue: Boolean = true,
        ): Long {
            val brand = brandService.register(브랜드_등록_커맨드())
            return productFacade.registerProduct(
                상품_등록_커맨드(
                    brandId = brand.id,
                    price = price,
                    initialStock = initialStock,
                    saleType = if (requiresWaitingQueue) ProductSaleType.LIMITED else ProductSaleType.NORMAL,
                ),
            ).id
        }

        private fun admittedToken(userId: Long): String {
            waitingQueueFacade.enter(userId)
            waitingQueueFacade.admitBatch()
            return waitingQueueFacade.position(userId).token ?: error("입장 토큰 발급 실패")
        }

        private fun placeOrder(
            productId: Long,
            quantity: Long,
            headers: HttpHeaders,
        ) = placeOrder(listOf(productId to quantity), headers)

        private fun placeOrder(
            items: List<Pair<Long, Long>>,
            headers: HttpHeaders,
        ) = testRestTemplate.exchange(
            "/api/v1/orders",
            HttpMethod.POST,
            HttpEntity(
                mapOf(
                    "items" to items.map { (productId, quantity) ->
                        mapOf(
                            "productId" to productId,
                            "quantity" to quantity,
                        )
                    },
                ),
                headers,
            ),
            mapResponseType,
        )

        private fun authHeaders(
            queueToken: String? = null,
            idempotencyKey: String?,
        ): HttpHeaders {
            val headers = HttpHeaders()
            headers.set("X-Loopers-LoginId", 기본_로그인_ID)
            headers.set("X-Loopers-LoginPw", 기본_비밀번호)
            idempotencyKey?.let { headers.set("Idempotency-Key", it) }
            queueToken?.let { headers.set("X-Queue-Token", it) }
            return headers
        }

        private fun Map<String, Any?>.number(key: String): Long =
            (get(key) as Number).toLong()

        private fun tokenState(token: String) =
            redissonClient.getMap<String, String>(
                "${waitingQueueProperties.redisKeyPrefix}:" +
                    "${waitingQueueProperties.redisKeys.tokenAdmissionPrefix}:$token",
            )

        private fun userAdmissionState(userId: Long) =
            redissonClient.getMap<String, String>(
                "${waitingQueueProperties.redisKeyPrefix}:" +
                    "${waitingQueueProperties.redisKeys.userAdmissionPrefix}:$userId",
            )
    }
