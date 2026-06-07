package com.loopers.application.order

import com.loopers.application.stock.StockApplicationService
import com.loopers.domain.user.EncodedPassword
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.infrastructure.user.UserJpaEntity
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

@SpringBootTest
class OrderFacadeIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val stockApplicationService: StockApplicationService,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val userJpaRepository: UserJpaRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("주문 생성 시, ")
    @Nested
    inner class CreateOrder {
        @DisplayName("상품 재고를 차감하고 주문 스냅샷을 저장한다.")
        @Test
        fun createOrder_deductsStockAndSavesSnapshot() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = saveProductWithStock(name = "Loopers T-Shirt", price = 10_000L, stock = 10)

            // act
            val order = orderFacade.createOrder(
                CreateOrderCommand(
                    userId = user.id,
                    items = listOf(
                        CreateOrderItemCommand(productId = product.id, quantity = 3),
                    ),
                ),
            )

            // assert
            val remainingStock = stockApplicationService.getStock(product.id)
            assertAll(
                { assertThat(order.userId).isEqualTo(user.id) },
                { assertThat(order.totalPrice).isEqualTo(30_000L) },
                { assertThat(order.items).hasSize(1) },
                { assertThat(order.items.first().productName).isEqualTo("Loopers T-Shirt") },
                { assertThat(order.items.first().productPrice).isEqualTo(10_000L) },
                { assertThat(order.items.first().quantity).isEqualTo(3) },
                { assertThat(remainingStock.quantity).isEqualTo(7) },
            )
        }

        @DisplayName("존재하지 않는 상품이 포함되면 주문 생성에 실패한다.")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())

            // act & assert
            val result = assertThrows<CoreException> {
                orderFacade.createOrder(
                    CreateOrderCommand(
                        userId = user.id,
                        items = listOf(CreateOrderItemCommand(productId = 999L, quantity = 1)),
                    ),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("재고가 부족하면 주문 생성에 실패하고 주문을 저장하지 않는다.")
        @Test
        fun throwsBadRequest_whenStockIsNotEnough() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val product = saveProductWithStock(stock = 2)

            // act & assert
            val result = assertThrows<CoreException> {
                orderFacade.createOrder(
                    CreateOrderCommand(
                        userId = user.id,
                        items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 3)),
                    ),
                )
            }

            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
                { assertThat(orderJpaRepository.findAll()).isEmpty() },
            )
        }

        @DisplayName("주문 상품이 비어있으면 주문 생성에 실패한다.")
        @Test
        fun throwsBadRequest_whenItemsAreEmpty() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())

            // act & assert
            val result = assertThrows<CoreException> {
                orderFacade.createOrder(
                    CreateOrderCommand(
                        userId = user.id,
                        items = emptyList(),
                    ),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }

        @DisplayName("여러 상품 중 하나가 실패하면 전체 주문 생성과 재고 차감을 롤백한다.")
        @Test
        fun rollsBackStockDeduction_whenAnyProductFails() {
            // arrange
            val user = userJpaRepository.save(newUserJpaEntity())
            val enoughProduct = saveProductWithStock(name = "Enough", stock = 10)
            val insufficientProduct = saveProductWithStock(name = "Insufficient", stock = 1)

            // act & assert
            val result = assertThrows<CoreException> {
                orderFacade.createOrder(
                    CreateOrderCommand(
                        userId = user.id,
                        items = listOf(
                            CreateOrderItemCommand(productId = enoughProduct.id, quantity = 3),
                            CreateOrderItemCommand(productId = insufficientProduct.id, quantity = 2),
                        ),
                    ),
                )
            }

            val enoughStock = stockApplicationService.getStock(enoughProduct.id)
            val insufficientStock = stockApplicationService.getStock(insufficientProduct.id)
            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST) },
                { assertThat(enoughStock.quantity).isEqualTo(10) },
                { assertThat(insufficientStock.quantity).isEqualTo(1) },
                { assertThat(orderJpaRepository.findAll()).isEmpty() },
            )
        }

        @DisplayName("존재하지 않는 유저이면 주문 생성에 실패한다.")
        @Test
        fun throwsNotFound_whenUserDoesNotExist() {
            // arrange
            val product = saveProductWithStock()

            // act & assert
            val result = assertThrows<CoreException> {
                orderFacade.createOrder(
                    CreateOrderCommand(
                        userId = 999L,
                        items = listOf(CreateOrderItemCommand(productId = product.id, quantity = 1)),
                    ),
                )
            }

            assertAll(
                { assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND) },
                { assertThat(orderJpaRepository.findAll()).isEmpty() },
            )
        }
    }

    /** product + stock 을 함께 저장하는 테스트 helper */
    private fun saveProductWithStock(
        brandId: Long = 1L,
        name: String = "Loopers T-Shirt",
        description: String = "매일 입기 좋은 티셔츠",
        price: Long = 10_000L,
        stock: Int = 10,
    ): ProductJpaEntity {
        val product = productJpaRepository.save(
            ProductJpaEntity(
                brandId = brandId,
                name = name,
                description = description,
                price = price,
                likeCount = 0,
            ),
        )
        stockJpaRepository.save(StockJpaEntity(productId = product.id, quantity = stock))
        return product
    }

    private fun newUserJpaEntity(
        loginId: String = "seondays",
        password: String = "\$2a\$10\$existingHashedPassword.",
        name: String = "선데이",
        birthDate: LocalDate = LocalDate.of(1990, 1, 1),
        email: String = "seondays@example.com",
    ) = UserJpaEntity(
        loginId = loginId,
        encodedPassword = EncodedPassword(password),
        name = name,
        birthDate = birthDate,
        email = email,
    )
}
