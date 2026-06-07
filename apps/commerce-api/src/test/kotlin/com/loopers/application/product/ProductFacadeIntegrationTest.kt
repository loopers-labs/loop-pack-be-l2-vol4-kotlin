package com.loopers.application.product

import com.loopers.domain.product.ProductSearchCondition
import com.loopers.domain.product.ProductSortType
import com.loopers.infrastructure.brand.BrandJpaEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.support.paging.PageCondition
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

@SpringBootTest
class ProductFacadeIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("상품 등록 시, ")
    @Nested
    inner class CreateProduct {
        @DisplayName("브랜드가 존재하면 상품을 등록한다.")
        @Test
        fun createProduct_whenBrandExists() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity(name = "Loopers"))

            // act
            val product = productFacade.createProduct(
                brandId = brand.id,
                name = "Loopers T-Shirt",
                description = "매일 입기 좋은 티셔츠",
                price = 10_000L,
                initialStock = 10,
            )

            // assert
            assertAll(
                { assertThat(product.id).isNotNull() },
                { assertThat(product.brandId).isEqualTo(brand.id) },
                { assertThat(product.name).isEqualTo("Loopers T-Shirt") },
                { assertThat(product.stock).isEqualTo(10) },
                { assertThat(product.soldOut).isFalse() },
            )
        }

        @DisplayName("브랜드가 존재하지 않으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            // act & assert
            val result = assertThrows<CoreException> {
                productFacade.createProduct(
                    brandId = 999L,
                    name = "Loopers T-Shirt",
                    description = "매일 입기 좋은 티셔츠",
                    price = 10_000L,
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("상품 상세 조회 시, ")
    @Nested
    inner class GetProductDetail {
        @DisplayName("상품과 브랜드 정보를 조합해 반환한다.")
        @Test
        fun getProductDetail() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity(name = "Loopers"))
            val product = saveProductWithStock(brandId = brand.id, name = "Loopers T-Shirt", stock = 0)

            // act
            val detail = productFacade.getProductDetail(product.id)

            // assert
            assertAll(
                { assertThat(detail.product.id).isEqualTo(product.id) },
                { assertThat(detail.product.name).isEqualTo("Loopers T-Shirt") },
                { assertThat(detail.product.stock).isEqualTo(0) },
                { assertThat(detail.product.soldOut).isTrue() },
                { assertThat(detail.brand.id).isEqualTo(brand.id) },
                { assertThat(detail.brand.name).isEqualTo("Loopers") },
            )
        }
    }

    @DisplayName("상품 목록 조회 시, ")
    @Nested
    inner class GetProducts {
        @DisplayName("상품 목록에 브랜드 이름을 조합해 반환한다.")
        @Test
        fun getProducts_withBrandName() {
            // arrange
            val loopers = brandJpaRepository.save(newBrandJpaEntity(name = "Loopers"))
            val outer = brandJpaRepository.save(newBrandJpaEntity(name = "Outer"))
            saveProductWithStock(brandId = loopers.id, name = "T-Shirt", price = 10_000L, stock = 0)
            saveProductWithStock(brandId = outer.id, name = "Jacket", price = 30_000L, stock = 3)

            // act
            val result = productFacade.getProducts(
                ProductSearchCondition(
                    sortType = ProductSortType.PRICE_ASC,
                    pageCondition = PageCondition(page = 0, size = 10),
                ),
            )

            // assert
            assertAll(
                { assertThat(result.items.map { it.name }).containsExactly("T-Shirt", "Jacket") },
                { assertThat(result.items.map { it.brandName }).containsExactly("Loopers", "Outer") },
                { assertThat(result.items.map { it.soldOut }).containsExactly(true, false) },
                { assertThat(result.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("같은 브랜드의 상품이 여러 개여도 브랜드 이름을 정상 조합한다.")
        @Test
        fun getProducts_withSameBrandProducts() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity(name = "Loopers"))
            saveProductWithStock(brandId = brand.id, name = "T-Shirt", price = 10_000L)
            saveProductWithStock(brandId = brand.id, name = "Hoodie", price = 20_000L)

            // act
            val result = productFacade.getProducts(
                ProductSearchCondition(
                    sortType = ProductSortType.PRICE_ASC,
                    pageCondition = PageCondition(page = 0, size = 10),
                ),
            )

            // assert
            assertAll(
                { assertThat(result.items.map { it.name }).containsExactly("T-Shirt", "Hoodie") },
                { assertThat(result.items.map { it.brandName }).containsExactly("Loopers", "Loopers") },
            )
        }

        @DisplayName("브랜드 조건이 존재하지 않는 브랜드이면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenBrandConditionDoesNotExist() {
            // act & assert
            val result = assertThrows<CoreException> {
                productFacade.getProducts(
                    ProductSearchCondition(
                        brandId = 999L,
                        pageCondition = PageCondition(page = 0, size = 10),
                    ),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("상품 수정 시, ")
    @Nested
    inner class UpdateProduct {
        @DisplayName("상품 정보를 수정하고 기존 재고 정보를 함께 반환한다.")
        @Test
        fun updateProduct_returnsUpdatedProductWithStock() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity(name = "Loopers"))
            val product = saveProductWithStock(brandId = brand.id, stock = 7)

            // act
            val updated = productFacade.updateProduct(
                productId = product.id,
                name = "Loopers Hoodie",
                description = "따뜻한 후드",
                price = 30_000L,
            )

            // assert
            assertAll(
                { assertThat(updated.id).isEqualTo(product.id) },
                { assertThat(updated.name).isEqualTo("Loopers Hoodie") },
                { assertThat(updated.description).isEqualTo("따뜻한 후드") },
                { assertThat(updated.price).isEqualTo(30_000L) },
                { assertThat(updated.stock).isEqualTo(7) },
            )
        }
    }

    @DisplayName("상품 삭제 시, ")
    @Nested
    inner class DeleteProduct {
        @DisplayName("상품과 재고를 함께 soft delete 처리한다.")
        @Test
        fun deleteProduct_deletesProductAndStock() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity(name = "Loopers"))
            val product = saveProductWithStock(brandId = brand.id, stock = 7)

            // act
            productFacade.deleteProduct(product.id)

            // assert
            val deletedProduct = productJpaRepository.findById(product.id).orElseThrow()
            val activeStock = stockJpaRepository.findByProductIdAndDeletedAtIsNull(product.id)
            assertAll(
                { assertThat(deletedProduct.deletedAt).isNotNull() },
                { assertThat(activeStock).isNull() },
            )
        }
    }

    private fun newBrandJpaEntity(
        name: String = "Loopers",
        description: String = "감성 이커머스 브랜드",
        logoImageUrl: String? = null,
    ) = BrandJpaEntity(
        name = name,
        description = description,
        logoImageUrl = logoImageUrl,
    )

    private fun newProductJpaEntity(
        brandId: Long,
        name: String = "Loopers T-Shirt",
        description: String = "매일 입기 좋은 티셔츠",
        price: Long = 10_000L,
        likeCount: Int = 0,
    ) = ProductJpaEntity(
        brandId = brandId,
        name = name,
        description = description,
        price = price,
        likeCount = likeCount,
    )

    private fun saveProductWithStock(
        brandId: Long,
        name: String = "Loopers T-Shirt",
        description: String = "매일 입기 좋은 티셔츠",
        price: Long = 10_000L,
        stock: Int = 10,
        likeCount: Int = 0,
    ): ProductJpaEntity {
        val product = productJpaRepository.save(
            newProductJpaEntity(
                brandId = brandId,
                name = name,
                description = description,
                price = price,
                likeCount = likeCount,
            ),
        )
        stockJpaRepository.save(StockJpaEntity(productId = product.id, quantity = stock))
        return product
    }
}
