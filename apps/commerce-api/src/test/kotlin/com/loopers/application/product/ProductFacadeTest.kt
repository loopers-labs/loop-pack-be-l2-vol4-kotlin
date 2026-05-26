package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.LikeCountQueryPort
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepositoryPort
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

class ProductFacadeTest {

    private lateinit var productRepositoryPort: ProductRepositoryPort
    private lateinit var stockRepositoryPort: StockRepositoryPort
    private lateinit var brandRepositoryPort: BrandRepositoryPort
    private lateinit var likeCountQueryPort: LikeCountQueryPort
    private lateinit var likeService: LikeService
    private lateinit var productFacade: ProductFacade

    @BeforeEach
    fun setUp() {
        productRepositoryPort = mockk()
        stockRepositoryPort = mockk()
        brandRepositoryPort = mockk()
        likeCountQueryPort = mockk()
        likeService = mockk(relaxed = true)
        productFacade = ProductFacade(
            productRepositoryPort,
            stockRepositoryPort,
            brandRepositoryPort,
            likeCountQueryPort,
            likeService,
        )
    }

    @DisplayName("getProduct 호출 시, ")
    @Nested
    inner class GetProduct {
        @DisplayName("상품/브랜드/재고/좋아요수를 조합해 ProductDetail을 반환한다.")
        @Test
        fun returnsDetail_whenAllExist() {
            // arrange
            val product = Product(id = 1L, name = "에어맥스", price = 100L, description = "d", brandId = 9L)
            val brand = Brand(id = 9L, name = "Nike", description = "x")
            val stock = Stock(id = 7L, productId = 1L, quantity = 50)
            every { productRepositoryPort.findByIdOrNull(1L) } returns product
            every { brandRepositoryPort.findByIdOrNull(9L) } returns brand
            every { stockRepositoryPort.findByProductId(1L) } returns stock
            every { likeCountQueryPort.countByProductId(1L) } returns 3L

            // act
            val result = productFacade.getProduct(1L)

            // assert
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.name).isEqualTo("에어맥스")
            assertThat(result.brandName).isEqualTo("Nike")
            assertThat(result.stockQuantity).isEqualTo(50)
            assertThat(result.likeCount).isEqualTo(3L)
        }

        @DisplayName("상품이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductMissing() {
            every { productRepositoryPort.findByIdOrNull(9999L) } returns null
            val result = assertThrows<CoreException> { productFacade.getProduct(9999L) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("createProduct 호출 시, ")
    @Nested
    inner class CreateProduct {
        @DisplayName("Brand가 존재하면 Product 저장 후 Stock도 함께 생성한다.")
        @Test
        fun savesProductAndStock_whenBrandExists() {
            // arrange
            val brand = Brand(id = 9L, name = "Nike", description = "x")
            val savedProduct = Product(id = 1L, name = "에어맥스", price = 100L, description = "d", brandId = 9L)
            val savedStock = Stock(id = 7L, productId = 1L, quantity = 50)
            every { brandRepositoryPort.findByIdOrNull(9L) } returns brand
            every { productRepositoryPort.save(any()) } returns savedProduct
            val capturedStock = slot<Stock>()
            every { stockRepositoryPort.save(capture(capturedStock)) } returns savedStock

            // act
            val result = productFacade.createProduct(
                CreateProductCommand(name = "에어맥스", price = 100L, description = "d", brandId = 9L, quantity = 50),
            )

            // assert
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.stockQuantity).isEqualTo(50)
            assertThat(capturedStock.captured.productId).isEqualTo(1L)
            assertThat(capturedStock.captured.quantity).isEqualTo(50)
            verify(exactly = 1) { productRepositoryPort.save(any()) }
            verify(exactly = 1) { stockRepositoryPort.save(any()) }
        }

        @DisplayName("Brand가 없으면 NOT_FOUND 예외가 발생하고 저장은 호출되지 않는다.")
        @Test
        fun throwsNotFound_whenBrandMissing() {
            every { brandRepositoryPort.findByIdOrNull(any()) } returns null
            val result = assertThrows<CoreException> {
                productFacade.createProduct(
                    CreateProductCommand(name = "x", price = 100L, description = "d", brandId = 999L, quantity = 10),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
            verify(exactly = 0) { productRepositoryPort.save(any()) }
            verify(exactly = 0) { stockRepositoryPort.save(any()) }
        }
    }

    @DisplayName("updateProduct 호출 시, ")
    @Nested
    inner class UpdateProduct {
        @DisplayName("brandId 변경 시도하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenBrandIdChanged() {
            val existing = Product(id = 1L, name = "old", price = 100L, description = "d", brandId = 9L)
            every { productRepositoryPort.findByIdOrNull(1L) } returns existing
            val result = assertThrows<CoreException> {
                productFacade.updateProduct(
                    UpdateProductCommand(id = 1L, name = "x", price = 200L, description = "d", brandId = 10L, quantity = 5),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            verify(exactly = 0) { productRepositoryPort.save(any()) }
        }

        @DisplayName("brandId가 동일하면 Product와 Stock이 함께 갱신된다.")
        @Test
        fun updatesProductAndStock_whenBrandIdSame() {
            val existing = Product(id = 1L, name = "old", price = 100L, description = "d", brandId = 9L)
            val existingStock = Stock(id = 7L, productId = 1L, quantity = 50)
            val brand = Brand(id = 9L, name = "Nike", description = "x")
            val updatedProduct = Product(id = 1L, name = "new", price = 200L, description = "newD", brandId = 9L)
            val updatedStock = Stock(id = 7L, productId = 1L, quantity = 100)

            every { productRepositoryPort.findByIdOrNull(1L) } returns existing
            every { productRepositoryPort.save(any()) } returns updatedProduct
            every { stockRepositoryPort.findByProductId(1L) } returns existingStock
            every { stockRepositoryPort.save(any()) } returns updatedStock
            every { brandRepositoryPort.findByIdOrNull(9L) } returns brand
            every { likeCountQueryPort.countByProductId(1L) } returns 0L

            val result = productFacade.updateProduct(
                UpdateProductCommand(id = 1L, name = "new", price = 200L, description = "newD", brandId = 9L, quantity = 100),
            )

            assertThat(result.name).isEqualTo("new")
            assertThat(result.stockQuantity).isEqualTo(100)
            verify(exactly = 1) { productRepositoryPort.save(any()) }
            verify(exactly = 1) { stockRepositoryPort.save(any()) }
        }
    }

    @DisplayName("deleteProduct 호출 시, ")
    @Nested
    inner class DeleteProduct {
        @DisplayName("Product와 Stock을 함께 delete한다.")
        @Test
        fun deletesProductAndStock_whenExists() {
            val product = Product(id = 1L, name = "x", price = 100L, description = "d", brandId = 9L)
            val stock = Stock(id = 7L, productId = 1L, quantity = 5)
            every { productRepositoryPort.findByIdOrNull(1L) } returns product
            every { stockRepositoryPort.findByProductId(1L) } returns stock
            every { productRepositoryPort.delete(product) } returns Unit
            every { stockRepositoryPort.delete(stock) } returns Unit

            productFacade.deleteProduct(1L)

            verify(exactly = 1) { productRepositoryPort.delete(product) }
            verify(exactly = 1) { stockRepositoryPort.delete(stock) }
        }

        @DisplayName("상품이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenProductMissing() {
            every { productRepositoryPort.findByIdOrNull(9999L) } returns null
            val result = assertThrows<CoreException> { productFacade.deleteProduct(9999L) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }
}
