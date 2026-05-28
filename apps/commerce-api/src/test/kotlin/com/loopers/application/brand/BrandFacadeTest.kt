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

        override fun existsByName(name: String): Boolean {
            return brands.any { it.name == name }
        }

        override fun save(brand: Brand): Brand {
            brands.removeIf { it.id == brand.id }
            brands.add(brand)
            return brand
        }
    }
}
