package com.loopers.domain.product.integration

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.infrastructure.persistence.product.ProductJpaRepository
import com.loopers.domain.product.infrastructure.persistence.stock.ProductStockJpaRepository
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.product.support.ProductSteps.Companion.상품_수정_커맨드
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class ProductFacadeIntegrationTest
    @Autowired
    constructor(
        private val brandService: BrandService,
        private val productFacade: ProductFacade,
        private val productJpaRepository: ProductJpaRepository,
        private val productStockJpaRepository: ProductStockJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `상품_등록은_초기_재고를_함께_저장한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())

            val product = productFacade.registerProduct(
                상품_등록_커맨드(
                    brandId = brand.id,
                    initialStock = 15,
                ),
            )

            val savedProduct = productJpaRepository.findById(product.id)
            val savedStock = productStockJpaRepository.findById(product.id).orElseThrow()
            assertThat(savedProduct).isPresent
            assertThat(savedStock.leftStock).isEqualTo(15)
        }

        @Test
        fun `초기_재고가_유효하지_않으면_상품과_재고가_저장되지_않는다`() {
            val brand = brandService.register(브랜드_등록_커맨드())

            val ex = assertThrows<CoreException> {
                productFacade.registerProduct(
                    상품_등록_커맨드(
                        brandId = brand.id,
                        initialStock = -1,
                    ),
                )
            }

            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(productJpaRepository.count()).isZero()
            assertThat(productStockJpaRepository.count()).isZero()
        }

        @Test
        fun `상품_수정_중_예외가_발생하면_기존_값이_유지된다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productFacade.registerProduct(상품_등록_커맨드(brandId = brand.id))

            val ex = assertThrows<CoreException> {
                productFacade.updateProduct(
                    productId = product.id,
                    command = 상품_수정_커맨드(name = "수정 상품", price = -1),
                )
            }

            val saved = productJpaRepository.findById(product.id).orElseThrow()
            assertThat(ex.errorType).isEqualTo(ErrorType.BAD_REQUEST)
            assertThat(saved.productName).isEqualTo("기본 상품")
            assertThat(saved.price).isEqualTo(10_000)
        }

        @Test
        fun `상품_삭제_중_예외가_발생하면_기존_상품이_유지된다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productFacade.registerProduct(상품_등록_커맨드(brandId = brand.id))

            val ex = assertThrows<CoreException> {
                productFacade.deleteProduct(product.id + 1_000)
            }

            val saved = productJpaRepository.findById(product.id).orElseThrow()
            assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
            assertThat(saved.deletedAt).isNull()
        }
    }
