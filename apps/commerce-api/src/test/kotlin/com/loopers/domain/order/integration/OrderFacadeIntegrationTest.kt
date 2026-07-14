package com.loopers.domain.order.integration

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.coupon.application.command.CouponTemplateCommand
import com.loopers.domain.coupon.application.service.CouponService
import com.loopers.domain.coupon.exception.CouponConflictException
import com.loopers.domain.coupon.infrastructure.persistence.issued.IssuedCouponJpaRepository
import com.loopers.domain.order.application.OrderFacade
import com.loopers.domain.order.infrastructure.persistence.OrderJpaRepository
import com.loopers.domain.order.support.OrderSteps.Companion.주문_생성_커맨드
import com.loopers.domain.order.support.OrderSteps.Companion.주문항목_생성_커맨드
import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.infrastructure.persistence.stock.ProductStockJpaRepository
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.user.application.service.UserService
import com.loopers.domain.user.support.UserSteps.Companion.사용자_회원가입
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest
class OrderFacadeIntegrationTest
    @Autowired
    constructor(
        private val userService: UserService,
        private val brandService: BrandService,
        private val productFacade: ProductFacade,
        private val couponService: CouponService,
        private val orderFacade: OrderFacade,
        private val orderJpaRepository: OrderJpaRepository,
        private val productStockJpaRepository: ProductStockJpaRepository,
        private val issuedCouponJpaRepository: IssuedCouponJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `같은_멱등키의_주문은_한_번만_저장하고_재고도_한_번만_차감한다`() {
            val user = userService.signUp(사용자_회원가입())
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productFacade.registerProduct(
                상품_등록_커맨드(
                    brandId = brand.id,
                    initialStock = 10,
                ),
            )
            val command = 주문_생성_커맨드(
                userId = user.id,
                idempotencyKey = "order-key-1",
                items = listOf(주문항목_생성_커맨드(productId = product.id, quantity = 2)),
            )

            val first = orderFacade.placeOrder(command)
            val second = orderFacade.placeOrder(command)

            val savedStock = productStockJpaRepository.findById(product.id).orElseThrow()
            assertThat(second.id).isEqualTo(first.id)
            assertThat(orderJpaRepository.count()).isEqualTo(1)
            assertThat(savedStock.leftStock).isEqualTo(8)
        }

        @Test
        fun `서로_다른_사용자는_같은_멱등키를_각자의_주문에_사용할_수_있다`() {
            val firstUser = userService.signUp(사용자_회원가입())
            val secondUser = userService.signUp(
                사용자_회원가입(loginId = "other1234", email = "other@example.com"),
            )
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productFacade.registerProduct(
                상품_등록_커맨드(brandId = brand.id, initialStock = 10),
            )
            val sharedKey = "same-key-different-users"
            val firstCommand = 주문_생성_커맨드(
                userId = firstUser.id,
                idempotencyKey = sharedKey,
                items = listOf(주문항목_생성_커맨드(productId = product.id, quantity = 1)),
            )
            val secondCommand = 주문_생성_커맨드(
                userId = secondUser.id,
                idempotencyKey = sharedKey,
                items = listOf(주문항목_생성_커맨드(productId = product.id, quantity = 1)),
            )

            val first = orderFacade.placeOrder(firstCommand)
            val second = orderFacade.placeOrder(secondCommand)

            assertThat(second.id).isNotEqualTo(first.id)
            assertThat(second.orderedUserId).isEqualTo(secondUser.id)
            assertThat(orderJpaRepository.count()).isEqualTo(2)
            assertThat(productStockJpaRepository.findById(product.id).orElseThrow().leftStock).isEqualTo(8)
        }

        @Test
        fun `동일_상품에_동시_주문이_몰려도_재고를_초과해_주문되지_않는다`() {
            val users = (1..10).map { index ->
                userService.signUp(
                    사용자_회원가입(
                        loginId = "user%04d".format(index),
                        email = "user$index@example.com",
                    ),
                )
            }
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productFacade.registerProduct(
                상품_등록_커맨드(
                    brandId = brand.id,
                    initialStock = 5,
                ),
            )
            val commands = users.map { user ->
                주문_생성_커맨드(
                    userId = user.id,
                    items = listOf(주문항목_생성_커맨드(productId = product.id, quantity = 1)),
                )
            }

            val results = executeConcurrently(commands) { command ->
                orderFacade.placeOrder(command).id
            }

            val savedStock = productStockJpaRepository.findById(product.id).orElseThrow()
            assertThat(results.count { it.isSuccess }).isEqualTo(5)
            assertThat(results.mapNotNull { it.errorType }).containsOnly(ErrorType.CONFLICT)
            assertThat(savedStock.leftStock).isEqualTo(0)
            assertThat(orderJpaRepository.count()).isEqualTo(5)
        }

        @Test
        fun `동일_쿠폰으로_동시_주문해도_한_번만_사용된다`() {
            val user = userService.signUp(사용자_회원가입())
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productFacade.registerProduct(
                상품_등록_커맨드(
                    brandId = brand.id,
                    initialStock = 10,
                ),
            )
            val template = couponService.createTemplate(
                쿠폰템플릿_생성_커맨드(
                    minOrderAmount = 0,
                ),
            )
            val issuedCoupon = couponService.issue(user.id, template.id)
            val commands = (1..10).map {
                주문_생성_커맨드(
                    userId = user.id,
                    issuedCouponId = issuedCoupon.id,
                    items = listOf(주문항목_생성_커맨드(productId = product.id, quantity = 1)),
                )
            }

            val results = executeConcurrently(commands) { command ->
                orderFacade.placeOrder(command).id
            }

            val savedStock = productStockJpaRepository.findById(product.id).orElseThrow()
            val savedCoupon = issuedCouponJpaRepository.findById(issuedCoupon.id).orElseThrow()
            assertThat(results.count { it.isSuccess }).isEqualTo(1)
            assertThat(results.mapNotNull { it.errorType }).containsOnly(ErrorType.CONFLICT)
            assertThat(savedStock.leftStock).isEqualTo(9)
            assertThat(savedCoupon.couponStatus).isEqualTo("USED")
            assertThat(savedCoupon.usedAt).isNotNull()
            assertThat(orderJpaRepository.count()).isEqualTo(1)
        }

        private fun <T> executeConcurrently(
            targets: List<T>,
            action: (T) -> Long,
        ): List<OrderAttemptResult> {
            val executor = Executors.newFixedThreadPool(targets.size)
            val ready = CountDownLatch(targets.size)
            val start = CountDownLatch(1)

            try {
                val futures = targets.map { target ->
                    executor.submit<OrderAttemptResult> {
                        ready.countDown()
                        start.await()
                        try {
                            OrderAttemptResult(orderId = action(target))
                        } catch (e: CoreException) {
                            OrderAttemptResult(errorType = e.errorType)
                        } catch (e: CouponConflictException) {
                            OrderAttemptResult(errorType = ErrorType.CONFLICT)
                        }
                    }
                }

                assertThat(ready.await(1, TimeUnit.SECONDS)).isTrue()
                start.countDown()
                return futures.map { it.get(10, TimeUnit.SECONDS) }
            } finally {
                executor.shutdownNow()
            }
        }

        private fun 쿠폰템플릿_생성_커맨드(
            name: String = "WELCOME_10",
            type: String = "FIXED",
            value: Long = 1_000,
            minOrderAmount: Long = 10_000,
        ): CouponTemplateCommand = CouponTemplateCommand(
            name = name,
            type = type,
            value = value,
            minOrderAmount = minOrderAmount,
            expiredAt = LocalDateTime.now().plusDays(7),
        )

        private data class OrderAttemptResult(
            val orderId: Long? = null,
            val errorType: ErrorType? = null,
        ) {
            val isSuccess: Boolean = orderId != null
        }
    }
