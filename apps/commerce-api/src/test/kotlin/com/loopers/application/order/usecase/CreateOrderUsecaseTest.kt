package com.loopers.application.order.usecase

import com.loopers.application.coupon.InMemoryCouponRepository
import com.loopers.application.coupon.InMemoryUserCouponRepository
import com.loopers.application.order.OrderCommand
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponType
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.order.OrderModel
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.UserService
import com.loopers.domain.withId
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZonedDateTime

class CreateOrderUsecaseTest {
    @DisplayName("주문 생성 유스케이스를 실행할 때,")
    @Nested
    inner class Execute {
        @DisplayName("회원 인증, 상품/재고 조회, 주문 저장 흐름을 조율한다.")
        @Test
        fun createsOrderAndDeductsStock() {
            // arrange
            val fixture = Fixture()

            // act
            val order = fixture.createOrderUsecase.execute(fixture.command())

            // assert
            assertAll(
                { assertThat(order.items).hasSize(2) },
                { assertThat(order.totalPrice).isEqualByComparingTo(BigDecimal("270000.00")) },
                { assertThat(fixture.stockRepository.findByProductId(10L)?.quantity).isEqualTo(8) },
                { assertThat(fixture.orderRepository.findById(order.id)).isNotNull() },
            )
        }

        @DisplayName("상품이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            val fixture = Fixture()

            // act
            val exception = assertThrows<CoreException> {
                fixture.createOrderUsecase.execute(
                    fixture.command(
                        items = listOf(OrderCommand.OrderItemCommand(productId = 999L, quantity = 1)),
                    ),
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("쿠폰을 적용하면 할인 금액이 반영되고 쿠폰은 사용 처리된다.")
        @Test
        fun appliesCouponDiscount() {
            // arrange
            val fixture = Fixture()
            val userCoupon = fixture.issueCoupon(discountValue = BigDecimal("1000.00"))

            // act
            val order = fixture.createOrderUsecase.execute(fixture.command(couponId = userCoupon.id))

            // assert
            assertAll(
                { assertThat(order.discountAmount).isEqualByComparingTo(BigDecimal("1000.00")) },
                { assertThat(order.paidPrice).isEqualByComparingTo(BigDecimal("269000.00")) },
                {
                    assertThat(fixture.userCouponRepository.findByIdAndUserId(userCoupon.id, fixture.userId)!!.status)
                        .isEqualTo(UserCouponStatus.USED)
                },
            )
        }

        @DisplayName("내 소유가 아닌 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenCouponBelongsToOtherUser() {
            // arrange
            val fixture = Fixture()
            val otherUserCoupon = fixture.issueCouponToOtherUser()

            // act
            val exception = assertThrows<CoreException> {
                fixture.createOrderUsecase.execute(fixture.command(couponId = otherUserCoupon.id))
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("이미 사용한 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenCouponAlreadyUsed() {
            // arrange
            val fixture = Fixture()
            val userCoupon = fixture.issueCoupon(discountValue = BigDecimal("1000.00"))
            fixture.createOrderUsecase.execute(fixture.command(couponId = userCoupon.id))

            // act
            val exception = assertThrows<CoreException> {
                fixture.createOrderUsecase.execute(fixture.command(couponId = userCoupon.id))
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    private class Fixture {
        private val userRepository = InMemoryUserRepository()
        private val productRepository = InMemoryProductRepository()
        private val couponRepository = InMemoryCouponRepository()
        val userCouponRepository = InMemoryUserCouponRepository()
        val stockRepository = InMemoryProductStockRepository()
        val orderRepository = InMemoryOrderRepository()
        val userId = 1L
        private val userService = UserService(userRepository)
        val createOrderUsecase = CreateOrderUsecase(
            userService = userService,
            productRepository = productRepository,
            productStockRepository = stockRepository,
            couponRepository = couponRepository,
            userCouponRepository = userCouponRepository,
            orderRepository = orderRepository,
        )

        init {
            userRepository.save(
                UserModel(
                    loginId = "loopers01",
                    rawPassword = "Pass1234!",
                    name = "홍길동",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "loopers@example.com",
                ).withId(userId),
            )
            productRepository.save(product(id = 10L, name = "Air Max", price = "120000.00"))
            productRepository.save(product(id = 20L, name = "Jordan", price = "30000.00"))
            stockRepository.save(ProductStockModel(productId = 10L, quantity = 10))
            stockRepository.save(ProductStockModel(productId = 20L, quantity = 5))
        }

        fun command(
            items: List<OrderCommand.OrderItemCommand> = listOf(
                OrderCommand.OrderItemCommand(productId = 10L, quantity = 2),
                OrderCommand.OrderItemCommand(productId = 20L, quantity = 1),
            ),
            couponId: Long? = null,
        ): OrderCommand {
            return OrderCommand(
                loginId = "loopers01",
                password = "Pass1234!",
                items = items,
                couponId = couponId,
            )
        }

        fun issueCoupon(discountValue: BigDecimal): UserCouponModel {
            val coupon = couponRepository.save(coupon(discountValue = discountValue))
            return userCouponRepository.save(UserCouponModel(userId = userId, couponId = coupon.id))
        }

        fun issueCouponToOtherUser(): UserCouponModel {
            val coupon = couponRepository.save(coupon(discountValue = BigDecimal("1000.00")))
            return userCouponRepository.save(UserCouponModel(userId = userId + 999, couponId = coupon.id))
        }

        private fun product(id: Long, name: String, price: String): ProductModel {
            return ProductModel(
                brandId = 1L,
                name = name,
                description = "Shoes",
                price = BigDecimal(price),
            ).withId(id)
        }

        private fun coupon(discountValue: BigDecimal): CouponModel {
            return CouponModel(
                name = "쿠폰",
                type = CouponType.FIXED,
                discountValue = discountValue,
                minOrderAmount = null,
                expiredAt = ZonedDateTime.now().plusDays(30),
            )
        }
    }

    private class InMemoryOrderRepository : OrderRepository {
        private val orders = mutableMapOf<Long, OrderModel>()
        private var nextId = 1L

        override fun save(order: OrderModel): OrderModel {
            val saved = order.withId(nextId++)
            orders[saved.id] = saved
            return saved
        }

        override fun findById(id: Long): OrderModel? {
            return orders[id]
        }
    }

    private class InMemoryUserRepository : UserRepository {
        private val users = mutableMapOf<Long, UserModel>()

        override fun save(user: UserModel): UserModel {
            users[user.id] = user
            return user
        }

        override fun findById(id: Long): UserModel? {
            return users[id]
        }

        override fun findByLoginId(loginId: String): UserModel? {
            return users.values.firstOrNull { it.loginId == loginId }
        }

        override fun existsByLoginId(loginId: String): Boolean {
            return findByLoginId(loginId) != null
        }
    }

    private class InMemoryProductRepository : ProductRepository {
        private val products = mutableMapOf<Long, ProductModel>()

        override fun save(product: ProductModel): ProductModel {
            products[product.id] = product
            return product
        }

        override fun findActiveById(id: Long): ProductModel? {
            return products[id]?.takeUnless { it.isDeleted() }
        }

        override fun findActiveAll(brandId: Long?, sort: ProductSort, pageable: Pageable): Page<ProductModel> {
            return PageImpl(products.values.toList(), pageable, products.size.toLong())
        }

        override fun existsActiveById(id: Long): Boolean {
            return findActiveById(id) != null
        }

        override fun incrementLikeCount(productId: Long) {
            findActiveById(productId)?.incrementLikeCount()
        }

        override fun decrementLikeCount(productId: Long) {
            findActiveById(productId)?.decrementLikeCount()
        }
    }

    private class InMemoryProductStockRepository : ProductStockRepository {
        private val stocks = mutableMapOf<Long, ProductStockModel>()

        override fun save(stock: ProductStockModel): ProductStockModel {
            stocks[stock.productId] = stock
            return stock
        }

        override fun findByProductId(productId: Long): ProductStockModel? {
            return stocks[productId]
        }

        override fun findByProductIdForUpdate(productId: Long): ProductStockModel? {
            return findByProductId(productId)
        }

        override fun findAllByProductIdIn(productIds: List<Long>): List<ProductStockModel> {
            return productIds.mapNotNull { stocks[it] }
        }
    }
}
