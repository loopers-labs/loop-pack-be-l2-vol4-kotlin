package com.loopers.application.brand

import com.loopers.application.product.CreateProductCommand
import com.loopers.application.product.ProductFacade
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
class BrandFacadeIntegrationTest @Autowired constructor(
    private val brandFacade: BrandFacade,
    private val brandRepositoryPort: BrandRepositoryPort,
    private val productFacade: ProductFacade,
    private val productRepositoryPort: ProductRepositoryPort,
    private val stockRepositoryPort: StockRepositoryPort,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("createBrand 통합 흐름")
    @Nested
    inner class CreateBrand {
        @DisplayName("새 브랜드를 등록하면, DB에 저장되고 id가 부여된다.")
        @Test
        fun savesBrand_whenValid() {
            // act
            val brand = brandFacade.createBrand(CreateBrandCommand(name = "Nike", description = "Just do it"))

            // assert
            assertThat(brand.id).isGreaterThan(0L)
            assertThat(brandRepositoryPort.findByIdOrNull(brand.id)?.name).isEqualTo("Nike")
        }

        @DisplayName("중복된 name으로 등록하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenDuplicateName() {
            // arrange
            brandFacade.createBrand(CreateBrandCommand(name = "Nike", description = "x"))

            // act
            val result = assertThrows<CoreException> {
                brandFacade.createBrand(CreateBrandCommand(name = "Nike", description = "y"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }
    }

    @DisplayName("updateBrand 통합 흐름")
    @Nested
    inner class UpdateBrand {
        @DisplayName("브랜드를 수정하면, name/description이 갱신된다.")
        @Test
        fun updatesBrand_whenValid() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "old"))

            // act
            val updated = brandFacade.updateBrand(UpdateBrandCommand(id = saved.id, name = "Nike2", description = "new"))

            // assert
            assertThat(updated.name).isEqualTo("Nike2")
            assertThat(updated.description).isEqualTo("new")
            assertThat(brandRepositoryPort.findByIdOrNull(saved.id)?.name).isEqualTo("Nike2")
        }

        @DisplayName("자기 자신의 name으로 수정하면, 충돌 없이 성공한다.")
        @Test
        fun updatesBrand_whenSameName() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "old"))

            // act
            val updated = brandFacade.updateBrand(UpdateBrandCommand(id = saved.id, name = "Nike", description = "new"))

            // assert
            assertThat(updated.name).isEqualTo("Nike")
            assertThat(updated.description).isEqualTo("new")
        }

        @DisplayName("다른 브랜드의 name과 충돌하면, CONFLICT 예외가 발생한다.")
        @Test
        fun throwsConflict_whenDuplicate() {
            // arrange
            brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val target = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "y"))

            // act
            val result = assertThrows<CoreException> {
                brandFacade.updateBrand(UpdateBrandCommand(id = target.id, name = "Nike", description = "z"))
            }

            // assert
            assertThat(result.errorType).isEqualTo(ErrorType.CONFLICT)
        }

        @DisplayName("존재하지 않는 id로 수정하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            val result = assertThrows<CoreException> {
                brandFacade.updateBrand(UpdateBrandCommand(id = 9999L, name = "x", description = "y"))
            }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("deleteBrand 통합 흐름")
    @Nested
    inner class DeleteBrand {
        @DisplayName("브랜드를 삭제하면, soft delete되어 조회되지 않는다.")
        @Test
        fun softDeletesBrand() {
            // arrange
            val saved = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))

            // act
            brandFacade.deleteBrand(saved.id)

            // assert
            assertThat(brandRepositoryPort.findByIdOrNull(saved.id)).isNull()
        }

        @DisplayName("존재하지 않는 id로 삭제하면, NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenMissing() {
            val result = assertThrows<CoreException> { brandFacade.deleteBrand(9999L) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("브랜드를 삭제하면, 그 브랜드의 모든 상품과 재고가 cascade soft delete된다.")
        @Test
        fun cascadeDeletesProductsAndStocks() {
            // arrange
            val brand = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val productDetails = (0 until 3).map {
                productFacade.createProduct(
                    CreateProductCommand(name = "p$it", price = 100L, description = "d", brandId = brand.id, quantity = 5),
                )
            }

            // act
            brandFacade.deleteBrand(brand.id)

            // assert
            assertThat(brandRepositoryPort.findByIdOrNull(brand.id)).isNull()
            productDetails.forEach {
                assertThat(productRepositoryPort.findByIdOrNull(it.id)).isNull()
                assertThat(stockRepositoryPort.findByProductId(it.id)).isNull()
            }
        }

        @DisplayName("브랜드를 삭제해도 다른 브랜드의 상품과 재고는 영향받지 않는다.")
        @Test
        fun cascadeDoesNotAffectOtherBrands() {
            val brand1 = brandRepositoryPort.save(Brand.create(name = "Nike", description = "x"))
            val brand2 = brandRepositoryPort.save(Brand.create(name = "Adidas", description = "y"))
            val survivor = productFacade.createProduct(
                CreateProductCommand(name = "survivor", price = 100L, description = "d", brandId = brand2.id, quantity = 5),
            )
            productFacade.createProduct(
                CreateProductCommand(name = "victim", price = 100L, description = "d", brandId = brand1.id, quantity = 5),
            )

            brandFacade.deleteBrand(brand1.id)

            assertThat(productRepositoryPort.findByIdOrNull(survivor.id)).isNotNull
            assertThat(stockRepositoryPort.findByProductId(survivor.id)).isNotNull
        }
    }

    @DisplayName("getBrands 통합 흐름")
    @Nested
    inner class GetBrands {
        @DisplayName("페이지 단위로 브랜드 목록을 반환한다.")
        @Test
        fun returnsPagedBrands() {
            // arrange
            repeat(3) { brandFacade.createBrand(CreateBrandCommand(name = "brand-$it", description = "d-$it")) }

            // act
            val result = brandFacade.getBrands(PageRequest(page = 0, size = 10))

            // assert
            assertThat(result.items).hasSize(3)
            assertThat(result.totalElements).isEqualTo(3L)
        }
    }
}
