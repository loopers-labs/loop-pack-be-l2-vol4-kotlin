package com.loopers.application.brand

import com.loopers.infrastructure.brand.BrandJpaEntity
import com.loopers.infrastructure.brand.BrandJpaRepository
import com.loopers.infrastructure.product.ProductJpaEntity
import com.loopers.infrastructure.product.ProductJpaRepository
import com.loopers.infrastructure.stock.StockJpaEntity
import com.loopers.infrastructure.stock.StockJpaRepository
import com.loopers.projection.product.ProductLikeCountProjectionEntity
import com.loopers.projection.product.ProductLikeCountQueryRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class BrandFacadeIntegrationTest @Autowired constructor(
    private val brandFacade: BrandFacade,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val stockJpaRepository: StockJpaRepository,
    private val productLikeCountQueryRepository: ProductLikeCountQueryRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("브랜드 삭제 시, ")
    @Nested
    inner class DeleteBrand {
        @DisplayName("해당 브랜드의 상품, 재고, 좋아요 집계를 모두 함께 삭제한다.")
        @Test
        fun deletesBrandWithAllProducts() {
            // arrange
            val brand = brandJpaRepository.save(
                BrandJpaEntity(name = "Loopers", description = "감성 브랜드", logoImageUrl = null),
            )
            val product1 = createProductWithStockAndPlc(brand.id, "T-Shirt", 10_000L, 5)
            val product2 = createProductWithStockAndPlc(brand.id, "Hoodie", 20_000L, 3)

            // act
            brandFacade.deleteBrand(brand.id)

            // assert
            val deletedBrand = brandJpaRepository.findById(brand.id).orElseThrow()
            val product1Entity = productJpaRepository.findById(product1.id).orElseThrow()
            val product2Entity = productJpaRepository.findById(product2.id).orElseThrow()
            val stock1 = stockJpaRepository.findByProductIdAndDeletedAtIsNull(product1.id)
            val stock2 = stockJpaRepository.findByProductIdAndDeletedAtIsNull(product2.id)
            val plc1 = productLikeCountQueryRepository.findById(product1.id).orElse(null)
            val plc2 = productLikeCountQueryRepository.findById(product2.id).orElse(null)
            assertAll(
                { assertThat(deletedBrand.deletedAt).isNotNull() },
                { assertThat(product1Entity.deletedAt).isNotNull() },
                { assertThat(product2Entity.deletedAt).isNotNull() },
                { assertThat(stock1).isNull() },
                { assertThat(stock2).isNull() },
                { assertThat(plc1).isNull() },
                { assertThat(plc2).isNull() },
            )
        }

        @DisplayName("상품이 없는 브랜드도 정상 삭제된다.")
        @Test
        fun deletesBrandWithoutProducts() {
            // arrange
            val brand = brandJpaRepository.save(
                BrandJpaEntity(name = "EmptyBrand", description = "상품 없는 브랜드", logoImageUrl = null),
            )

            // act
            brandFacade.deleteBrand(brand.id)

            // assert
            val deletedBrand = brandJpaRepository.findById(brand.id).orElseThrow()
            assertThat(deletedBrand.deletedAt).isNotNull()
        }
    }

    private fun createProductWithStockAndPlc(
        brandId: Long,
        name: String,
        price: Long,
        stock: Int,
    ): ProductJpaEntity {
        val product = productJpaRepository.save(
            ProductJpaEntity(brandId = brandId, name = name, description = "$name 설명", price = price),
        )
        stockJpaRepository.save(StockJpaEntity(productId = product.id, quantity = stock))
        productLikeCountQueryRepository.save(
            ProductLikeCountProjectionEntity(productId = product.id, brandId = brandId, likeCount = 0),
        )
        return product
    }
}
