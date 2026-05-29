package com.loopers.application.brand

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepository
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
            val brandFacade = BrandFacade(BrandService(brandRepository))
            brandRepository.save(ProductBrandFixture.createBrand(id = 1L, name = "loopers"))

            val result = brandFacade.getBrand(1L)

            assertThat(result.name).isEqualTo("loopers")
        }

        @DisplayName("삭제된 브랜드는 조회할 수 없다")
        @Test
        fun throwsNotFound_whenBrandIsDeleted() {
            val brandRepository = FakeBrandRepository()
            val brandFacade = BrandFacade(BrandService(brandRepository))
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
            return brands.find { it.id == brandId }
        }

        override fun findAllByIds(brandIds: Collection<Long>): List<Brand> {
            return brands.filter { it.id in brandIds }
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
}
