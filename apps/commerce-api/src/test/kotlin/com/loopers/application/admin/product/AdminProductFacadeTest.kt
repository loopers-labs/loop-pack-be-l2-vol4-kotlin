package com.loopers.application.admin.product

import com.loopers.application.brand.BrandService
import com.loopers.application.product.ProductService
import com.loopers.application.product.dto.ProductCreateCommand
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.application.product.dto.ProductUpdateCommand
import com.loopers.application.productstat.ProductStatService
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductCatalogService
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.domain.productstat.ProductStat
import com.loopers.domain.productstat.ProductStatRepository
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
import org.springframework.data.domain.PageRequest

class AdminProductFacadeTest {
    @DisplayName("관리자 상품 삭제")
    @Nested
    inner class DeleteProduct {
        @DisplayName("등록된 상품을 삭제한다")
        @Test
        fun deletesProduct() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))

            fixture.adminProductFacade.deleteProduct(10L)

            val result = fixture.productRepository.findById(10L)
            assertThat(result?.isDeleted).isTrue()
        }

        @DisplayName("존재하지 않는 상품은 삭제할 수 없다")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            val fixture = AdminProductFacadeFixture()

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.deleteProduct(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("이미 삭제된 상품은 삭제할 수 없다")
        @Test
        fun throwsNotFound_whenProductIsDeleted() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L, isDeleted = true))

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.deleteProduct(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드의 상품은 삭제할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers", isDeleted = true))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.deleteProduct(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("관리자 상품 수정")
    @Nested
    inner class UpdateProduct {
        @DisplayName("등록된 상품 기본 정보를 수정한다")
        @Test
        fun updatesProduct() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))
            fixture.productStatRepository.save(ProductStat(productId = 10L, likeCount = 5L))

            val result = fixture.adminProductFacade.updateProduct(
                productId = 10L,
                command = ProductUpdateCommand(
                    name = "updated hoodie",
                    price = 20_000L,
                    description = "updated product",
                    imageUrl = "https://image.loopers/updated.png",
                ),
            )

            assertAll(
                { assertThat(result.productId).isEqualTo(10L) },
                { assertThat(result.productName).isEqualTo("updated hoodie") },
                { assertThat(result.price).isEqualTo(20_000L) },
                { assertThat(result.brand.brandId).isEqualTo(1L) },
                { assertThat(result.likeCount).isEqualTo(5L) },
            )
        }

        @DisplayName("존재하지 않는 상품은 수정할 수 없다")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            val fixture = AdminProductFacadeFixture()

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.updateProduct(
                    productId = 10L,
                    command = ProductUpdateCommand(
                        name = "updated hoodie",
                        price = 20_000L,
                        description = "updated product",
                        imageUrl = "https://image.loopers/updated.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 수정할 수 없다")
        @Test
        fun throwsNotFound_whenProductIsDeleted() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L, isDeleted = true))

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.updateProduct(
                    productId = 10L,
                    command = ProductUpdateCommand(
                        name = "updated hoodie",
                        price = 20_000L,
                        description = "updated product",
                        imageUrl = "https://image.loopers/updated.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드의 상품은 수정할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers", isDeleted = true))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.updateProduct(
                    productId = 10L,
                    command = ProductUpdateCommand(
                        name = "updated hoodie",
                        price = 20_000L,
                        description = "updated product",
                        imageUrl = "https://image.loopers/updated.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("관리자 상품 등록")
    @Nested
    inner class CreateProduct {
        @DisplayName("기존 브랜드에 상품을 등록한다")
        @Test
        fun createsProduct() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))

            val result = fixture.adminProductFacade.createProduct(
                ProductCreateCommand(
                    brandId = 1L,
                    name = "loopers hoodie",
                    price = 10_000L,
                    description = "loopers product",
                    imageUrl = "https://image.loopers/product.png",
                ),
            )

            assertAll(
                { assertThat(result.productId).isEqualTo(1L) },
                { assertThat(result.productName).isEqualTo("loopers hoodie") },
                { assertThat(result.brand.brandId).isEqualTo(1L) },
                { assertThat(result.likeCount).isEqualTo(0L) },
            )
        }

        @DisplayName("존재하지 않는 브랜드에는 상품을 등록할 수 없다")
        @Test
        fun throwsNotFound_whenBrandDoesNotExist() {
            val fixture = AdminProductFacadeFixture()

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.createProduct(
                    ProductCreateCommand(
                        brandId = 1L,
                        name = "loopers hoodie",
                        price = 10_000L,
                        description = "loopers product",
                        imageUrl = "https://image.loopers/product.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드에는 상품을 등록할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers", isDeleted = true))

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.createProduct(
                    ProductCreateCommand(
                        brandId = 1L,
                        name = "loopers hoodie",
                        price = 10_000L,
                        description = "loopers product",
                        imageUrl = "https://image.loopers/product.png",
                    ),
                )
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("관리자 상품 상세 조회")
    @Nested
    inner class GetProduct {
        @DisplayName("등록된 상품 상세 정보를 조회한다")
        @Test
        fun returnsProductDetail() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))
            fixture.productStatRepository.save(ProductStat(productId = 10L, likeCount = 3L))

            val result = fixture.adminProductFacade.getProduct(10L)

            assertAll(
                { assertThat(result.productId).isEqualTo(10L) },
                { assertThat(result.brand.name).isEqualTo("loopers") },
                { assertThat(result.likeCount).isEqualTo(3L) },
            )
        }

        @DisplayName("존재하지 않는 상품은 조회할 수 없다")
        @Test
        fun throwsNotFound_whenProductDoesNotExist() {
            val fixture = AdminProductFacadeFixture()

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.getProduct(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 상품은 조회할 수 없다")
        @Test
        fun throwsNotFound_whenProductIsDeleted() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L, isDeleted = true))

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.getProduct(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("삭제된 브랜드의 상품은 조회할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val fixture = AdminProductFacadeFixture()
            fixture.brandRepository.save(createBrand(id = 1L, name = "loopers", isDeleted = true))
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))

            val result = assertThrows<CoreException> {
                fixture.adminProductFacade.getProduct(10L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("관리자 상품 목록 조회")
    @Nested
    inner class GetProducts {
        @DisplayName("등록된 상품 목록을 페이지로 조회한다")
        @Test
        fun returnsProductPage() {
            val fixture = AdminProductFacadeFixture()
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))
            fixture.productRepository.save(createProduct(id = 20L, brandId = 2L))

            val result = fixture.adminProductFacade.getProducts(
                ProductListCommand(
                    brandId = null,
                    sort = ProductSort.LATEST,
                    page = 0,
                    size = 20,
                ),
            )

            assertAll(
                { assertThat(result.content).hasSize(2) },
                { assertThat(result.totalElements).isEqualTo(2L) },
                { assertThat(result.content.map { it.productId }).containsExactly(20L, 10L) },
            )
        }

        @DisplayName("브랜드 ID가 주어지면 해당 브랜드 상품만 조회한다")
        @Test
        fun returnsProductPageFilteredByBrandId() {
            val fixture = AdminProductFacadeFixture()
            fixture.productRepository.save(createProduct(id = 10L, brandId = 1L))
            fixture.productRepository.save(createProduct(id = 20L, brandId = 2L))

            val result = fixture.adminProductFacade.getProducts(
                ProductListCommand(
                    brandId = 1L,
                    sort = ProductSort.LATEST,
                    page = 0,
                    size = 20,
                ),
            )

            assertAll(
                { assertThat(result.content).hasSize(1) },
                { assertThat(result.content[0].brandId).isEqualTo(1L) },
            )
        }
    }

    private class AdminProductFacadeFixture {
        val brandRepository = FakeBrandRepository()
        val productRepository = FakeProductRepository()
        val productStatRepository = FakeProductStatRepository()
        val adminProductFacade = AdminProductFacade(
            productService = ProductService(productRepository),
            brandService = BrandService(brandRepository),
            productStatService = ProductStatService(productStatRepository),
            productCatalogService = ProductCatalogService(),
        )
    }

    private class FakeBrandRepository : BrandRepository {
        private val brands = mutableListOf<Brand>()

        override fun findById(brandId: Long): Brand? {
            return brands.find { it.id == brandId }
        }

        override fun findDisplayable(page: Int, size: Int): Page<Brand> {
            return PageImpl(emptyList(), PageRequest.of(page, size), 0)
        }

        override fun existsByName(name: String): Boolean {
            return brands.any { it.name == name }
        }

        override fun save(brand: Brand): Brand {
            brands.removeIf { it.id == brand.id }
            brands.add(brand)
            return brand
        }

        override fun update(brand: Brand): Brand {
            brands.removeIf { it.id == brand.id }
            brands.add(brand)
            return brand
        }
    }

    private class FakeProductRepository : ProductRepository {
        private val products = mutableListOf<Product>()
        private var sequence = 1L

        override fun findById(productId: Long): Product? {
            return products.find { it.id == productId }
        }

        override fun findAllByBrandId(brandId: Long): List<Product> {
            return products.filter { it.brandId == brandId }
        }

        override fun findDisplayableSummaries(
            brandId: Long?,
            sort: ProductSort,
            page: Int,
            size: Int,
        ): Page<ProductSummary> {
            val content = products
                .filter { !it.isDeleted }
                .filter { brandId == null || it.brandId == brandId }
                .sortedWith(compareByDescending<Product> { it.id })
                .drop(page * size)
                .take(size)
                .map {
                    ProductSummary(
                        productId = it.id,
                        productName = it.name,
                        price = it.price,
                        imageUrl = it.imageUrl,
                        brandId = it.brandId,
                        brandName = "brand-${it.brandId}",
                        likeCount = 0L,
                    )
                }

            val total = products
                .filter { !it.isDeleted }
                .count { brandId == null || it.brandId == brandId }

            return PageImpl(
                content,
                PageRequest.of(page, size),
                total.toLong(),
            )
        }

        override fun save(product: Product): Product {
            val saved = if (product.id == 0L) {
                Product(
                    id = sequence++,
                    brandId = product.brandId,
                    name = product.name,
                    price = product.price,
                    description = product.description,
                    imageUrl = product.imageUrl,
                    isDeleted = product.isDeleted,
                )
            } else {
                product
            }
            products.removeIf { it.id == saved.id }
            products.add(saved)
            return saved
        }

        override fun update(product: Product): Product {
            products.removeIf { it.id == product.id }
            products.add(product)
            return product
        }

        override fun updateAll(products: Collection<Product>): List<Product> {
            products.forEach(::save)
            return products.toList()
        }
    }

    private class FakeProductStatRepository : ProductStatRepository {
        private val productStats = mutableListOf<ProductStat>()

        override fun findByProductId(productId: Long): ProductStat? {
            return productStats.find { it.productId == productId }
        }

        override fun findAllByProductIds(productIds: Collection<Long>): List<ProductStat> {
            return productStats.filter { it.productId in productIds }
        }

        override fun save(productStat: ProductStat): ProductStat {
            productStats.removeIf { it.productId == productStat.productId }
            productStats.add(productStat)
            return productStat
        }
    }

    private fun createBrand(
        id: Long,
        name: String,
        isDeleted: Boolean = false,
    ): Brand {
        return Brand(
            id = id,
            name = name,
            description = "$name brand",
            logoImageUrl = "https://image.loopers/$name.png",
            isDeleted = isDeleted,
        )
    }

    private fun createProduct(
        id: Long,
        brandId: Long,
        isDeleted: Boolean = false,
    ): Product {
        return Product(
            id = id,
            brandId = brandId,
            name = "loopers hoodie",
            price = 10_000L,
            description = "loopers product",
            imageUrl = "https://image.loopers/product.png",
            isDeleted = isDeleted,
        )
    }
}
