package com.loopers.application.order

import com.loopers.application.order.usecase.CreateOrderUsecase
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductStockModel
import com.loopers.domain.product.ProductStockRepository
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.runConcurrently
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class OrderStockConcurrencyTest @Autowired constructor(
    private val createOrderUsecase: CreateOrderUsecase,
    private val userService: UserService,
    private val productRepository: ProductRepository,
    private val productStockRepository: ProductStockRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("동일 상품에 동시 주문이 몰려도 재고는 정확히 차감되고 오버셀이 발생하지 않는다.")
    @Test
    fun deductsStockExactly_withoutOversell() {
        // arrange
        val threadCount = 10
        val initialStock = 5
        val product = productRepository.save(
            ProductModel(brandId = 1L, name = "상품", description = "설명", price = BigDecimal("10000")),
        )
        productStockRepository.save(ProductStockModel(productId = product.id, quantity = initialStock))
        val users = (1..threadCount).map { signUp("buyer$it") }

        // act
        val errors = runConcurrently(threadCount) { index ->
            createOrderUsecase.execute(
                OrderCommand(
                    loginId = users[index].loginId,
                    password = PASSWORD,
                    items = listOf(OrderCommand.OrderItemCommand(productId = product.id, quantity = 1)),
                ),
            )
        }

        // assert
        val finalStock = productStockRepository.findByProductId(product.id)!!.quantity
        assertAll(
            { assertThat(errors).hasSize(threadCount - initialStock) },
            {
                assertThat(errors).allSatisfy {
                    assertThat((it as CoreException).errorType).isEqualTo(ErrorType.CONFLICT)
                }
            },
            { assertThat(finalStock).isEqualTo(0) },
        )
    }

    private fun signUp(loginId: String) = userService.signUp(
        UserService.SignUpCommand(
            loginId = loginId,
            password = PASSWORD,
            name = "구매자",
            birthDate = LocalDate.of(1990, 1, 1),
            email = "$loginId@loopers.com",
        ),
    )

    companion object {
        private const val PASSWORD = "Password1!"
    }
}
