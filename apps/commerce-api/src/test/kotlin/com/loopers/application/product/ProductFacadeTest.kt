package com.loopers.application.product

import com.loopers.application.brand.BrandService
import com.loopers.application.product.dto.ProductListCommand
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
import com.loopers.fixture.product.ProductBrandFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class ProductFacadeTest {
    @DisplayName("상품 목록 조회")
    @Nested
    inner class GetProducts {
        @DisplayName("상품 목록에 브랜드명과 좋아요 수를 포함한다")
        @Test
        fun returnsProductSummariesWithBrandAndLikeCount() {
            val fixture = ProductServiceFixture()
            fixture.brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))
            fixture.productStatRepository.save(ProductBrandFixture.createProductStat(productId = 10L, likeCount = 3L))

            val result = fixture.productFacade.getProducts(
                ProductListCommand(
                    brandId = null,
                    sort = ProductSort.LATEST,
                    page = 0,
                    size = 20,
                ),
            )

            assertAll(
                { assertThat(result.content).hasSize(1) },
                { assertThat(result.content[0].brandName).isEqualTo("loopers") },
                { assertThat(result.content[0].likeCount).isEqualTo(3L) },
            )
        }
    }

    @DisplayName("상품 상세 조회")
    @Nested
    inner class GetProduct {
        @DisplayName("상품 상세에 브랜드와 좋아요 수를 포함한다")
        @Test
        fun returnsProductDetailWithBrandAndLikeCount() {
            val fixture = ProductServiceFixture()
            fixture.brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))
            fixture.productRepository.save(ProductBrandFixture.createProduct(id = 10L, brandId = 1L))
            fixture.productStatRepository.save(ProductBrandFixture.createProductStat(productId = 10L, likeCount = 3L))

            val result = fixture.productFacade.getProduct(10L)

            assertAll(
                { assertThat(result.brand.name).isEqualTo("loopers") },
                { assertThat(result.likeCount).isEqualTo(3L) },
            )
        }
    }

    private class ProductServiceFixture {
        val brandRepository = FakeBrandRepository()
        val productRepository = FakeProductRepository()
        val productStatRepository = FakeProductStatRepository()
        val productFacade = ProductFacade(
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
            val content = brands
                .filter { !it.isDeleted }
                .drop(page * size)
                .take(size)

            return PageImpl(
                content,
                PageRequest.of(page, size),
                brands.count { !it.isDeleted }.toLong(),
            )
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

        override fun findById(productId: Long): Product? {
            return products.find { it.id == productId }
        }

        override fun findDisplayableSummaries(
            brandId: Long?,
            sort: ProductSort,
            page: Int,
            size: Int,
        ): Page<ProductSummary> {
            val items = products
                .filter { !it.isDeleted }
                .filter { brandId == null || it.brandId == brandId }
                .drop(page * size)
                .take(size)
                .map {
                    ProductSummary(
                        productId = it.id,
                        productName = it.name,
                        price = it.price,
                        imageUrl = it.imageUrl,
                        brandId = it.brandId,
                        brandName = "loopers",
                        likeCount = 3L,
                    )
                }

            return PageImpl(
                items,
                PageRequest.of(page, size),
                products.size.toLong(),
            )
        }

        override fun save(product: Product): Product {
            products.removeIf { it.id == product.id }
            products.add(product)
            return product
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
}
