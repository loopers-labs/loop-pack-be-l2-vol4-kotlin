package com.loopers.application.brand

import com.loopers.application.product.ProductService
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.fixture.product.ProductBrandFixture
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class BrandFacadeTest {
    @DisplayName("브랜드 조회")
    @Nested
    inner class GetBrand {
        @DisplayName("삭제되지 않은 브랜드를 조회한다")
        @Test
        fun returnsBrandInfo_whenBrandExists() {
            val brandRepository = FakeBrandRepository()
            val brandFacade = createFacade(brandRepository)
            brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))

            val result = brandFacade.getBrand(1L)

            assertThat(result.name).isEqualTo("loopers")
        }

        @DisplayName("삭제된 브랜드는 조회할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val brandRepository = FakeBrandRepository()
            val brandFacade = createFacade(brandRepository)
            brandRepository.save(ProductBrandFixture.createBrand(id = 1L, isDeleted = true))

            val result = assertThrows<CoreException> {
                brandFacade.getBrand(1L)
            }

            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    private class FakeBrandRepository : BrandRepository {
        private val brands = mutableListOf<Brand>()

        override fun findById(brandId: Long): Brand? {
            return brands.find { it.id == brandId && !it.isDeleted }
        }

        override fun findAllByIds(brandIds: Collection<Long>): List<Brand> {
            return brands.filter { it.id in brandIds && !it.isDeleted }
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
        override fun findById(productId: Long): Product? {
            return null
        }

        override fun findAllByIds(productIds: Collection<Long>): List<Product> {
            return emptyList()
        }

        override fun findAllByBrandId(brandId: Long): List<Product> {
            return emptyList()
        }

        override fun findDisplayableSummaries(
            brandId: Long?,
            sort: ProductSort,
            page: Int,
            size: Int,
        ): Page<ProductSummary> {
            return PageImpl(emptyList(), PageRequest.of(page, size), 0)
        }

        override fun save(product: Product): Product {
            return product
        }

        override fun existsByBrandIdAndName(brandId: Long, name: String): Boolean {
            return false
        }

        override fun existsByBrandIdAndNameAndIdNot(brandId: Long, name: String, productId: Long): Boolean {
            return false
        }

        override fun update(product: Product): Product {
            return product
        }

        override fun updateAll(products: Collection<Product>): List<Product> {
            return products.toList()
        }
    }

    private fun createFacade(
        brandRepository: FakeBrandRepository,
        productRepository: FakeProductRepository = FakeProductRepository(),
    ): BrandFacade {
        return BrandFacade(
            brandService = BrandService(brandRepository),
            productService = ProductService(productRepository),
        )
    }
}
