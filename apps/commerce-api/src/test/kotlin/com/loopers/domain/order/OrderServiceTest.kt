package com.loopers.domain.order

import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserRepository
import com.loopers.domain.withId
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.LocalDate

class OrderServiceTest {
    @DisplayName("주문할 때,")
    @Nested
    inner class Order {
        @DisplayName("여러 상품 주문을 생성하고 각 상품 재고를 차감한다.")
        @Test
        fun createsOrderAndDeductsStocks() {
            // arrange
            val fixture = Fixture()

            // act
            val order = fixture.orderService.order(
                OrderService.OrderCommand(
                    userId = 1L,
                    items = listOf(
                        OrderService.OrderItemCommand(productId = 10L, quantity = 2),
                        OrderService.OrderItemCommand(productId = 20L, quantity = 1),
                    ),
                ),
            )

            // assert
            assertAll(
                { assertThat(order.items).hasSize(2) },
                { assertThat(order.totalPrice).isEqualByComparingTo(BigDecimal("270000.00")) },
                { assertThat(fixture.stockRepository.findByProductId(10L)?.quantity).isEqualTo(8) },
                { assertThat(fixture.stockRepository.findByProductId(20L)?.quantity).isEqualTo(4) },
                { assertThat(fixture.productRepository.findActiveById(10L)?.stockQuantity).isEqualTo(8) },
            )
        }

        @DisplayName("회원이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // arrange
            val fixture = Fixture()

            // act
            val exception = assertThrows<CoreException> {
                fixture.orderService.order(
                    OrderService.OrderCommand(
                        userId = 999L,
                        items = listOf(OrderService.OrderItemCommand(productId = 10L, quantity = 1)),
                    ),
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("상품이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            val fixture = Fixture()

            // act
            val exception = assertThrows<CoreException> {
                fixture.orderService.order(
                    OrderService.OrderCommand(
                        userId = 1L,
                        items = listOf(OrderService.OrderItemCommand(productId = 999L, quantity = 1)),
                    ),
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("재고가 부족하면 CONFLICT 예외가 발생하고 어떤 재고도 차감하지 않는다.")
        @Test
        fun throwsConflictAndDoesNotDeductAnyStock_whenStockIsInsufficient() {
            // arrange
            val fixture = Fixture()

            // act
            val exception = assertThrows<CoreException> {
                fixture.orderService.order(
                    OrderService.OrderCommand(
                        userId = 1L,
                        items = listOf(
                            OrderService.OrderItemCommand(productId = 10L, quantity = 2),
                            OrderService.OrderItemCommand(productId = 20L, quantity = 99),
                        ),
                    ),
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.CONFLICT)
            assertThat(fixture.stockRepository.findByProductId(10L)?.quantity).isEqualTo(10)
            assertThat(fixture.stockRepository.findByProductId(20L)?.quantity).isEqualTo(5)
        }

        @DisplayName("같은 상품이 중복으로 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenSameProductIsDuplicated() {
            // arrange
            val fixture = Fixture()

            // act
            val exception = assertThrows<CoreException> {
                fixture.orderService.order(
                    OrderService.OrderCommand(
                        userId = 1L,
                        items = listOf(
                            OrderService.OrderItemCommand(productId = 10L, quantity = 1),
                            OrderService.OrderItemCommand(productId = 10L, quantity = 1),
                        ),
                    ),
                )
            }

            // assert
            assertThat(exception.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(fixture.stockRepository.findByProductId(10L)?.quantity).isEqualTo(10)
        }
    }

    private class Fixture {
        val userRepository = InMemoryUserRepository()
        val productRepository = InMemoryProductRepository()
        val stockRepository = InMemoryProductStockRepository()
        private val orderRepository = InMemoryOrderRepository()
        val orderService = OrderService(
            orderRepository = orderRepository,
            userRepository = userRepository,
            productRepository = productRepository,
            productStockRepository = stockRepository,
        )

        init {
            userRepository.save(
                UserModel(
                    loginId = "loopers01",
                    rawPassword = "Pass1234!",
                    name = "홍길동",
                    birthDate = LocalDate.of(1990, 1, 1),
                    email = "loopers@example.com",
                ).withId(1L),
            )
            productRepository.save(product(id = 10L, name = "Air Max", price = "120000.00", stockQuantity = 10))
            productRepository.save(product(id = 20L, name = "Jordan", price = "30000.00", stockQuantity = 5))
            stockRepository.save(ProductStockModel(productId = 10L, quantity = 10))
            stockRepository.save(ProductStockModel(productId = 20L, quantity = 5))
        }

        private fun product(id: Long, name: String, price: String, stockQuantity: Int): ProductModel {
            return ProductModel(
                brandId = 1L,
                name = name,
                description = "Shoes",
                price = BigDecimal(price),
                stockQuantity = stockQuantity,
            ).withId(id)
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

        override fun findActiveAll(brandId: Long?, sort: ProductSort): List<ProductModel> {
            return products.values.toList()
        }

        override fun existsActiveById(id: Long): Boolean {
            return findActiveById(id) != null
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
    }
}
