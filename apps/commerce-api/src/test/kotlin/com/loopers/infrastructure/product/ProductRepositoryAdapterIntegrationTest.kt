package com.loopers.infrastructure.product

import com.loopers.domain.common.PageRequest
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepositoryPort
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductRepositoryAdapterIntegrationTest @Autowired constructor(
    private val productRepositoryPort: ProductRepositoryPort,
    private val productJpaRepository: ProductJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("save 호출 시, ")
    @Nested
    inner class Save {
        @DisplayName("id가 0인 Product를 저장하면 id가 부여되고 INSERT된다.")
        @Test
        fun insertsProduct_whenIdIsZero() {
            val saved = productRepositoryPort.save(
                Product.create(name = "에어맥스", price = 100L, description = "d", brandId = 1L),
            )
            assertThat(saved.id).isGreaterThan(0L)
            assertThat(productJpaRepository.findById(saved.id)).isPresent
        }

        @DisplayName("id가 있는 Product를 저장하면 UPDATE된다(brandId는 보존).")
        @Test
        fun updatesProduct_whenIdExists() {
            val saved = productRepositoryPort.save(
                Product.create(name = "에어맥스", price = 100L, description = "old", brandId = 5L),
            )
            val updated = productRepositoryPort.save(saved.update(name = "new", price = 200L, description = "newD"))
            assertThat(updated.id).isEqualTo(saved.id)
            assertThat(updated.name).isEqualTo("new")
            assertThat(updated.price).isEqualTo(200L)
            assertThat(updated.brandId).isEqualTo(5L)
        }
    }

    @DisplayName("delete 호출 시, ")
    @Nested
    inner class Delete {
        @DisplayName("Product가 soft delete되어 일반 조회로 보이지 않는다.")
        @Test
        fun softDeletesProduct() {
            val saved = productRepositoryPort.save(
                Product.create(name = "에어맥스", price = 100L, description = "d", brandId = 1L),
            )

            productRepositoryPort.delete(saved)

            assertThat(productJpaRepository.findById(saved.id)).isEmpty
            assertThat(productRepositoryPort.findByIdOrNull(saved.id)).isNull()
        }
    }

    @DisplayName("findAll/findAllByBrandId 호출 시, ")
    @Nested
    inner class FindAll {
        @DisplayName("findAll은 페이지/사이즈에 맞춰 결과를 반환한다.")
        @Test
        fun returnsPagedResult() {
            repeat(5) { idx ->
                productRepositoryPort.save(
                    Product.create(name = "p$idx", price = 100L, description = "d", brandId = 1L),
                )
            }
            val firstPage = productRepositoryPort.findAll(PageRequest(page = 0, size = 3))
            assertThat(firstPage.items).hasSize(3)
            assertThat(firstPage.totalElements).isEqualTo(5L)
            assertThat(firstPage.totalPages).isEqualTo(2)
        }

        @DisplayName("findAllByBrandId(brandId, pageRequest)는 해당 브랜드의 상품만 반환한다.")
        @Test
        fun returnsByBrandId() {
            repeat(3) { productRepositoryPort.save(Product.create(name = "n$it", price = 100L, description = "d", brandId = 1L)) }
            repeat(2) { productRepositoryPort.save(Product.create(name = "a$it", price = 100L, description = "d", brandId = 2L)) }

            val brand1 = productRepositoryPort.findAllByBrandId(1L, PageRequest(page = 0, size = 10))
            val brand2 = productRepositoryPort.findAllByBrandId(2L, PageRequest(page = 0, size = 10))

            assertThat(brand1.items).hasSize(3)
            assertThat(brand2.items).hasSize(2)
        }

        @DisplayName("findAllByBrandId(brandId)는 페이지 없이 전체 리스트를 반환한다(cascade 용).")
        @Test
        fun returnsListByBrandId() {
            repeat(5) { productRepositoryPort.save(Product.create(name = "n$it", price = 100L, description = "d", brandId = 7L)) }
            val list = productRepositoryPort.findAllByBrandId(7L)
            assertThat(list).hasSize(5)
        }
    }
}
