package com.loopers.application.product

import com.loopers.domain.product.ProductSearchCondition
import com.loopers.domain.product.ProductSortType
import com.loopers.infrastructure.brand.BrandJpaEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
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
                stock = 10,
            )

            // assert
            assertAll(
                { assertThat(product.id).isNotNull() },
                { assertThat(product.brandId).isEqualTo(brand.id) },
                { assertThat(product.name).isEqualTo("Loopers T-Shirt") },
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
                    stock = 10,
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
            val product = productJpaRepository.save(newProductJpaEntity(brandId = brand.id, name = "Loopers T-Shirt"))

            // act
            val detail = productFacade.getProductDetail(product.id)

            // assert
            assertAll(
                { assertThat(detail.product.id).isEqualTo(product.id) },
                { assertThat(detail.product.name).isEqualTo("Loopers T-Shirt") },
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
            productJpaRepository.save(newProductJpaEntity(brandId = loopers.id, name = "T-Shirt", price = 10_000L))
            productJpaRepository.save(newProductJpaEntity(brandId = outer.id, name = "Jacket", price = 30_000L))

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
                { assertThat(result.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("같은 브랜드의 상품이 여러 개여도 브랜드 이름을 정상 조합한다.")
        @Test
        fun getProducts_withSameBrandProducts() {
            // arrange
            val brand = brandJpaRepository.save(newBrandJpaEntity(name = "Loopers"))
            productJpaRepository.save(newProductJpaEntity(brandId = brand.id, name = "T-Shirt", price = 10_000L))
            productJpaRepository.save(newProductJpaEntity(brandId = brand.id, name = "Hoodie", price = 20_000L))

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
        stock: Int = 10,
        likeCount: Int = 0,
    ) = ProductJpaEntity(
        brandId = brandId,
        name = name,
        description = description,
        price = price,
        stock = stock,
        likeCount = likeCount,
    )
}
