package com.loopers.domain.product

import com.loopers.domain.stock.Stock
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductServiceTest {

    private lateinit var productRepositoryPort: ProductRepositoryPort
    private lateinit var stockRepositoryPort: StockRepositoryPort
    private lateinit var productService: ProductService

    @BeforeEach
    fun setUp() {
        productRepositoryPort = mockk()
        stockRepositoryPort = mockk()
        productService = ProductService(productRepositoryPort, stockRepositoryPort)
    }

    @DisplayName("getById 호출 시, ")
    @Nested
    inner class GetById {
        @DisplayName("상품이 존재하면 도메인 객체를 반환한다.")
        @Test
        fun returnsProduct_whenExists() {
            val product = Product(id = 1L, name = "에어맥스", price = 100L, description = "d", brandId = 9L)
            every { productRepositoryPort.findById(1L) } returns product

            val result = productService.getById(1L)

            assertThat(result).isEqualTo(product)
        }

        @DisplayName("상품이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            every { productRepositoryPort.findById(9999L) } returns null

            val result = assertThrows<CoreException> { productService.getById(9999L) }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("create 호출 시, ")
    @Nested
    inner class Create {
        @DisplayName("Product를 저장하고 Stock도 함께 생성한다.")
        @Test
        fun savesProductAndStock() {
            val savedProduct = Product(id = 1L, name = "에어맥스", price = 100L, description = "d", brandId = 9L)
            val savedStock = Stock(id = 7L, productId = 1L, quantity = 50)
            every { productRepositoryPort.save(any()) } returns savedProduct
            val capturedStock = slot<Stock>()
            every { stockRepositoryPort.save(capture(capturedStock)) } returns savedStock

            val (product, stock) = productService.create(
                name = "에어맥스",
                price = 100L,
                description = "d",
                brandId = 9L,
                quantity = 50,
            )

            assertThat(product.id).isEqualTo(1L)
            assertThat(stock.quantity).isEqualTo(50)
            assertThat(capturedStock.captured.productId).isEqualTo(1L)
            verify(exactly = 1) { productRepositoryPort.save(any()) }
            verify(exactly = 1) { stockRepositoryPort.save(any()) }
        }
    }

    @DisplayName("update 호출 시, ")
    @Nested
    inner class Update {
        @DisplayName("brandId 변경 시도하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenBrandIdChanged() {
            val existing = Product(id = 1L, name = "old", price = 100L, description = "d", brandId = 9L)
            every { productRepositoryPort.findById(1L) } returns existing

            val result = assertThrows<CoreException> {
                productService.update(id = 1L, name = "x", price = 200L, description = "d", brandId = 10L, quantity = 5)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            verify(exactly = 0) { productRepositoryPort.save(any()) }
        }

        @DisplayName("brandId가 동일하면 Product와 Stock이 함께 갱신된다.")
        @Test
        fun updatesProductAndStock_whenBrandIdSame() {
            val existing = Product(id = 1L, name = "old", price = 100L, description = "d", brandId = 9L)
            val existingStock = Stock(id = 7L, productId = 1L, quantity = 50)
            val updatedProduct = Product(id = 1L, name = "new", price = 200L, description = "newD", brandId = 9L)
            val updatedStock = Stock(id = 7L, productId = 1L, quantity = 100)

            every { productRepositoryPort.findById(1L) } returns existing
            every { productRepositoryPort.save(any()) } returns updatedProduct
            every { stockRepositoryPort.findByProductId(1L) } returns existingStock
            every { stockRepositoryPort.save(any()) } returns updatedStock

            val (product, stock) = productService.update(
                id = 1L,
                name = "new",
                price = 200L,
                description = "newD",
                brandId = 9L,
                quantity = 100,
            )

            assertThat(product.name).isEqualTo("new")
            assertThat(stock.quantity).isEqualTo(100)
            verify(exactly = 1) { productRepositoryPort.save(any()) }
            verify(exactly = 1) { stockRepositoryPort.save(any()) }
        }
    }

    @DisplayName("delete 호출 시, ")
    @Nested
    inner class Delete {
        @DisplayName("Product와 Stock을 함께 delete한다.")
        @Test
        fun deletesProductAndStock_whenExists() {
            val product = Product(id = 1L, name = "x", price = 100L, description = "d", brandId = 9L)
            val stock = Stock(id = 7L, productId = 1L, quantity = 5)
            every { productRepositoryPort.findById(1L) } returns product
            every { stockRepositoryPort.findByProductId(1L) } returns stock
            every { productRepositoryPort.delete(product) } returns Unit
            every { stockRepositoryPort.delete(stock) } returns Unit

            productService.delete(1L)

            verify(exactly = 1) { productRepositoryPort.delete(product) }
            verify(exactly = 1) { stockRepositoryPort.delete(stock) }
        }

        @DisplayName("상품이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductMissing() {
            every { productRepositoryPort.findById(9999L) } returns null
            val result = assertThrows<CoreException> { productService.delete(9999L) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
