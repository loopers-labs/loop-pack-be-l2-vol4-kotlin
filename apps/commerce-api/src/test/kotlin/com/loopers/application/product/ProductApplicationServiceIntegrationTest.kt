package com.loopers.application.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.common.PageRequest
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.LikeCountQueryPort
import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.interfaces.api.product.ProductAdminApplicationServicePort
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
class ProductApplicationServiceIntegrationTest @Autowired constructor(
    private val productApplicationService: ProductAdminApplicationServicePort,
    private val productRepositoryPort: ProductRepositoryPort,
    private val stockRepositoryPort: StockRepositoryPort,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val likeService: LikeService,
    private val likeCountQueryPort: LikeCountQueryPort,
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
            val detail = productApplicationService.createProduct(
                CreateProductCommand(name = "에어맥스", price = 100000L, description = "d", brandId = brand.id, quantity = 50),
            )

            assertThat(detail.id).isGreaterThan(0L)
            assertThat(detail.stockQuantity).isEqualTo(50)
            assertThat(stockRepositoryPort.findByProductId(detail.id)?.quantity).isEqualTo(50)
        }

        @DisplayName("createProduct 시 LikeCount가 0으로 초기화된다.")
        @Test
        fun initializesLikeCountToZero() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val detail = productApplicationService.createProduct(
                CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand.id, quantity = 10),
            )

            assertThat(likeCountQueryPort.countByProductId(detail.id)).isEqualTo(0L)
        }

        @DisplayName("Brand가 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenBrandMissing() {
            val result = assertThrows<CoreException> {
                productApplicationService.createProduct(
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
            val detail = productApplicationService.createProduct(
                CreateProductCommand(name = "old", price = 100L, description = "d", brandId = brand.id, quantity = 10),
            )

            val updated = productApplicationService.updateProduct(
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
            val detail = productApplicationService.createProduct(
                CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand1.id, quantity = 10),
            )

            val result = assertThrows<CoreException> {
                productApplicationService.updateProduct(
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
            val detail = productApplicationService.createProduct(
                CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand.id, quantity = 10),
            )

            productApplicationService.deleteProduct(detail.id)

            assertThat(productRepositoryPort.findById(detail.id)).isNull()
            assertThat(stockRepositoryPort.findByProductId(detail.id)).isNull()
        }

        @DisplayName("deleteProduct 시 Like와 LikeCount도 cascade hard delete된다.")
        @Test
        fun cascadesLikeAndLikeCount() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val detail = productApplicationService.createProduct(
                CreateProductCommand(name = "p", price = 100L, description = "d", brandId = brand.id, quantity = 10),
            )
            likeService.register(userId = 1L, productId = detail.id)
            likeService.register(userId = 2L, productId = detail.id)
            assertThat(likeCountQueryPort.countByProductId(detail.id)).isEqualTo(2L)

            productApplicationService.deleteProduct(detail.id)

            assertThat(likeCountQueryPort.countByProductId(detail.id)).isEqualTo(0L)
            assertThat(likeService.findAllProductIdsByUserId(1L)).doesNotContain(detail.id)
            assertThat(likeService.findAllProductIdsByUserId(2L)).doesNotContain(detail.id)
        }
    }

    @DisplayName("getProduct / getProducts 통합 흐름")
    @Nested
    inner class Query {
        @DisplayName("getProduct는 brandName, stockQuantity, likeCount(초기값 0)를 포함한 ProductDetail을 반환한다.")
        @Test
        fun returnsDetail() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val created = productApplicationService.createProduct(
                CreateProductCommand(name = "에어맥스", price = 100L, description = "d", brandId = brand.id, quantity = 20),
            )

            val detail = productApplicationService.getProduct(created.id)

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
                productApplicationService.createProduct(CreateProductCommand(name = "n$it", price = 100L, description = "d", brandId = brand1.id, quantity = 1))
            }
            productApplicationService.createProduct(CreateProductCommand(name = "a", price = 100L, description = "d", brandId = brand2.id, quantity = 1))

            val result = productApplicationService.getProducts(null, PageRequest(page = 0, size = 10))

            assertThat(result.items).hasSize(3)
        }

        @DisplayName("getProducts(brandId=특정)은 해당 브랜드 상품만 반환한다.")
        @Test
        fun returnsFilteredByBrand() {
            val brand1 = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val brand2 = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "y"))
            repeat(2) {
                productApplicationService.createProduct(CreateProductCommand(name = "n$it", price = 100L, description = "d", brandId = brand1.id, quantity = 1))
            }
            productApplicationService.createProduct(CreateProductCommand(name = "a", price = 100L, description = "d", brandId = brand2.id, quantity = 1))

            val result = productApplicationService.getProducts(brand1.id, PageRequest(page = 0, size = 10))

            assertThat(result.items).hasSize(2)
            assertThat(result.items.all { it.brandId == brand1.id }).isTrue()
        }

        @DisplayName("getProducts는 각 상품의 좋아요 수(likeCount)를 포함해 반환한다.")
        @Test
        fun includesLikeCountPerSummary() {
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val popularProductId = productApplicationService.createProduct(
                CreateProductCommand(name = "popular", price = 100L, description = "d", brandId = brand.id, quantity = 1),
            ).id
            val quietProductId = productApplicationService.createProduct(
                CreateProductCommand(name = "quiet", price = 100L, description = "d", brandId = brand.id, quantity = 1),
            ).id
            likeService.register(userId = 1L, productId = popularProductId)
            likeService.register(userId = 2L, productId = popularProductId)
            likeService.register(userId = 3L, productId = popularProductId)

            val result = productApplicationService.getProducts(null, PageRequest(page = 0, size = 10))

            val byId = result.items.associate { it.id to it.likeCount }
            assertThat(byId[popularProductId]).isEqualTo(3L)
            assertThat(byId[quietProductId]).isEqualTo(0L)
        }
    }
}
