package com.loopers.domain.product

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.math.BigDecimal

class ProductCatalogServiceTest {
    @DisplayName("상품 상세를 조회할 때,")
    @Nested
    inner class GetDetail {
        @DisplayName("상품과 브랜드 정보를 조합하고 좋아요 수를 함께 반환한다.")
        @Test
        fun returnsProductDetailWithBrandAndLikeCount() {
            // arrange
            val brandRepository = InMemoryBrandRepository()
            val productRepository = InMemoryProductRepository()
            val brand = brandRepository.save(BrandModel(name = "Nike", description = "Brand").withId(1L))
            val product = productRepository.save(
                ProductModel(
                    brandId = brand.id,
                    name = "Air Max",
                    description = "Shoes",
                    price = BigDecimal("120000.00"),
                    likeCount = 5,
                    stockQuantity = 10,
                ).withId(10L),
            )
            val service = ProductCatalogService(productRepository = productRepository, brandRepository = brandRepository)

            // act
            val detail = service.getDetail(product.id)

            // assert
            assertAll(
                { assertThat(detail.product.name).isEqualTo("Air Max") },
                { assertThat(detail.brand.name).isEqualTo("Nike") },
                { assertThat(detail.product.likeCount).isEqualTo(5) },
            )
        }
    }

    @DisplayName("상품 목록을 조회할 때,")
    @Nested
    inner class GetProducts {
        @DisplayName("좋아요 수 내림차순 정렬을 적용한다.")
        @Test
        fun returnsProductsSortedByLikesDesc() {
            // arrange
            val brandRepository = InMemoryBrandRepository()
            val productRepository = InMemoryProductRepository()
            brandRepository.save(BrandModel(name = "Nike", description = "Brand").withId(1L))
            productRepository.save(product(id = 1L, name = "A", likeCount = 1))
            productRepository.save(product(id = 2L, name = "B", likeCount = 10))
            productRepository.save(product(id = 3L, name = "C", likeCount = 3))
            val service = ProductCatalogService(productRepository = productRepository, brandRepository = brandRepository)

            // act
            val products = service.getProducts(ProductCatalogService.ProductQuery(sort = ProductSort.LIKES_DESC))

            // assert
            assertThat(products.map { it.product.name }).containsExactly("B", "C", "A")
        }
    }

    private fun product(id: Long, name: String, likeCount: Int): ProductModel {
        return ProductModel(
            brandId = 1L,
            name = name,
            description = "Product",
            price = BigDecimal("1000.00"),
            likeCount = likeCount,
            stockQuantity = 10,
        ).withId(id)
    }

    private class InMemoryBrandRepository : BrandRepository {
        private val brands = mutableMapOf<Long, BrandModel>()

        override fun save(brand: BrandModel): BrandModel {
            brands[brand.id] = brand
            return brand
        }

        override fun findById(id: Long): BrandModel? {
            return brands[id]
        }

        override fun findActiveById(id: Long): BrandModel? {
            return brands[id]?.takeUnless { it.isDeleted() }
        }

        override fun existsActiveById(id: Long): Boolean {
            return findActiveById(id) != null
        }
    }

    private class InMemoryProductRepository : ProductRepository {
        private val products = mutableMapOf<Long, ProductModel>()

        override fun save(product: ProductModel): ProductModel {
            products[product.id] = product
            return product
        }

        override fun findActiveById(id: Long): ProductModel? {
            return products[id]?.takeUnless { it.isDeleted() }
        }

        override fun findActiveAll(brandId: Long?, sort: ProductSort): List<ProductModel> {
            val filtered = products.values
                .filterNot { it.isDeleted() }
                .filter { brandId == null || it.brandId == brandId }

            return when (sort) {
                ProductSort.LATEST -> filtered.sortedByDescending { it.id }
                ProductSort.PRICE_ASC -> filtered.sortedWith(compareBy<ProductModel> { it.price }.thenBy { it.id })
                ProductSort.LIKES_DESC -> filtered.sortedWith(compareByDescending<ProductModel> { it.likeCount }.thenByDescending { it.id })
            }
        }

        override fun existsActiveById(id: Long): Boolean {
            return findActiveById(id) != null
        }
    }
}
