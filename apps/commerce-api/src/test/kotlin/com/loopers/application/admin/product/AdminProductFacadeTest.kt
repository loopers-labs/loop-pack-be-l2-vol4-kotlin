package com.loopers.application.admin.product

import com.loopers.application.product.ProductService
import com.loopers.application.product.dto.ProductListCommand
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class AdminProductFacadeTest {
    @DisplayName("관리자 상품 목록 조회")
    @Nested
    inner class GetProducts {
        @DisplayName("등록된 상품 목록을 페이지로 조회한다")
        @Test
        fun returnsProductPage() {
            val productRepository = FakeProductRepository()
            val adminProductFacade = AdminProductFacade(ProductService(productRepository))
            productRepository.save(createProduct(id = 10L, brandId = 1L))
            productRepository.save(createProduct(id = 20L, brandId = 2L))

            val result = adminProductFacade.getProducts(
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
            val productRepository = FakeProductRepository()
            val adminProductFacade = AdminProductFacade(ProductService(productRepository))
            productRepository.save(createProduct(id = 10L, brandId = 1L))
            productRepository.save(createProduct(id = 20L, brandId = 2L))

            val result = adminProductFacade.getProducts(
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

    private class FakeProductRepository : ProductRepository {
        private val products = mutableListOf<Product>()

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
            products.removeIf { it.id == product.id }
            products.add(product)
            return product
        }

        override fun updateAll(products: Collection<Product>): List<Product> {
            products.forEach(::save)
            return products.toList()
        }
    }

    private fun createProduct(
        id: Long,
        brandId: Long,
    ): Product {
        return Product(
            id = id,
            brandId = brandId,
            name = "loopers hoodie",
            price = 10_000L,
            description = "loopers product",
            imageUrl = "https://image.loopers/product.png",
        )
    }
}
