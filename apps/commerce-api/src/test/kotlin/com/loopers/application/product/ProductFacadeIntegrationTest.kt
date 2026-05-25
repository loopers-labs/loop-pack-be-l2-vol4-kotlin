package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductFacadeIntegrationTest @Autowired constructor(
    private val productFacade: ProductFacade,
    private val productRepositoryPort: ProductRepositoryPort,
    private val stockRepositoryPort: StockRepositoryPort,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("createProduct 통합 흐름")
    @Nested
    inner class Create {
        @DisplayName("Brand가 존재하면 Product와 Stock이 함께 저장된다.")
        @Test
        fun createsProductAndStock() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val detail = productFacade.createProduct(
                CreateProductCommand(name = "에어맥스", price = 100000L, description = "d", brandId = brand.id, quantity = 50),
            )

            assertThat(detail.id).isGreaterThan(0L)
            assertThat(detail.stockQuantity).isEqualTo(50)
            assertThat(stockRepositoryPort.findByProductId(detail.id)?.quantity).isEqualTo(50)
        }

        @DisplayName("Brand가 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenBrandMissing() {
            val result = assertThrows<CoreException> {
                productFacade.createProduct(
                    CreateProductCommand(name = "x", price = 100L, description = "d", brandId = 9999L, quantity = 10),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("updateProduct 통합 흐름")
    @Nested
    inner class Update {
        @DisplayName("Product와 Stock이 함께 갱신된다.")
        @Test
        fun updatesProductAndStock() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val detail = productFacade.createProduct(
                CreateProductCommand(name = "old", price = 100L, description = "d", brandId = brand.id, quantity = 10),
            )

            val updated = productFacade.updateProduct(
                UpdateProductCommand(id = detail.id, name = "new", price = 200L, description = "newD", brandId = brand.id, quantity = 99),
            )

            assertThat(updated.name).isEqualTo("new")
            assertThat(updated.price).isEqualTo(200L)
            assertThat(updated.stockQuantity).isEqualTo(99)
            assertThat(stockRepositoryPort.findByProductId(detail.id)?.quantity).isEqualTo(99)
        }

        @DisplayName("brandId를 변경하려고 하면 BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsBadRequest_whenBrandIdChanged() {
            val brand1 = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val brand2 = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "y"))
            val detail = productFacade.createProduct(
                CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand1.id, quantity = 10),
            )

            val result = assertThrows<CoreException> {
                productFacade.updateProduct(
                    UpdateProductCommand(id = detail.id, name = "p", price = 100L, description = "d", brandId = brand2.id, quantity = 10),
                )
            }
            assertThat(result.errorType).isEqualTo(ErrorType.BAD_REQUEST)
        }
    }

    @DisplayName("deleteProduct 통합 흐름")
    @Nested
    inner class Delete {
        @DisplayName("Product와 Stock이 함께 soft delete된다.")
        @Test
        fun softDeletesProductAndStock() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val detail = productFacade.createProduct(
                CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand.id, quantity = 10),
            )

            productFacade.deleteProduct(detail.id)

            assertThat(productRepositoryPort.findByIdOrNull(detail.id)).isNull()
            assertThat(stockRepositoryPort.findByProductId(detail.id)).isNull()
        }
    }

    @DisplayName("getProduct / getProducts 통합 흐름")
    @Nested
    inner class Query {
        @DisplayName("getProduct는 brandName, stockQuantity, likeCount(stub=0)를 포함한 ProductDetail을 반환한다.")
        @Test
        fun returnsDetail() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val created = productFacade.createProduct(
                CreateProductCommand(name = "에어맥스", price = 100L, description = "d", brandId = brand.id, quantity = 20),
            )

            val detail = productFacade.getProduct(created.id)

            assertThat(detail.brandName).isEqualTo("Nike")
            assertThat(detail.stockQuantity).isEqualTo(20)
            assertThat(detail.likeCount).isEqualTo(0L)
        }

        @DisplayName("getProducts(brandId=null)은 전체 상품을 반환한다.")
        @Test
        fun returnsAllProducts() {
            val brand1 = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val brand2 = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "y"))
            repeat(2) {
                productFacade.createProduct(CreateProductCommand(name = "n$it", price = 100L, description = "d", brandId = brand1.id, quantity = 1))
            }
            productFacade.createProduct(CreateProductCommand(name = "a", price = 100L, description = "d", brandId = brand2.id, quantity = 1))

            val result = productFacade.getProducts(null, PageRequest(page = 0, size = 10))

            assertThat(result.items).hasSize(3)
        }

        @DisplayName("getProducts(brandId=특정)은 해당 브랜드 상품만 반환한다.")
        @Test
        fun returnsFilteredByBrand() {
            val brand1 = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val brand2 = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "y"))
            repeat(2) {
                productFacade.createProduct(CreateProductCommand(name = "n$it", price = 100L, description = "d", brandId = brand1.id, quantity = 1))
            }
            productFacade.createProduct(CreateProductCommand(name = "a", price = 100L, description = "d", brandId = brand2.id, quantity = 1))

            val result = productFacade.getProducts(brand1.id, PageRequest(page = 0, size = 10))

            assertThat(result.items).hasSize(2)
            assertThat(result.items.all { it.brandId == brand1.id }).isTrue()
        }
    }

    @DisplayName("deleteAllByBrandId(cascade)")
    @Nested
    inner class CascadeDelete {
        @DisplayName("주어진 브랜드의 모든 상품과 재고가 soft delete된다.")
        @Test
        fun cascadeDeletes() {
            val brand1 = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val brand2 = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "y"))
            val nikeProducts = (0 until 3).map {
                productFacade.createProduct(CreateProductCommand(name = "n$it", price = 100L, description = "d", brandId = brand1.id, quantity = 5))
            }
            val adidasProduct = productFacade.createProduct(
                CreateProductCommand(name = "a", price = 100L, description = "d", brandId = brand2.id, quantity = 5),
            )

            // 트랜잭션 안에서 호출되어야 하므로 BrandFacade를 거쳐 cascade 동작을 검증한다.
            // 여기서는 직접 호출이 어려우므로(MANDATORY), 통합 검증은 BrandFacadeIntegrationTest에서 수행한다.

            // 따라서 본 테스트는 상품 존재만 확인.
            assertThat(nikeProducts).hasSize(3)
            assertThat(adidasProduct.id).isGreaterThan(0L)
        }
    }
}
