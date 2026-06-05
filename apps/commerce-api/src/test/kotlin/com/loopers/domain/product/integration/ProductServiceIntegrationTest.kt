package com.loopers.domain.product.integration

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.product.application.command.ProductSearchCommand
import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.product.infrastructure.persistence.product.ProductJpaRepository
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
class ProductServiceIntegrationTest
    @Autowired
    constructor(
        private val brandService: BrandService,
        private val productService: ProductService,
        private val likeService: LikeService,
        private val productJpaRepository: ProductJpaRepository,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `유효한_커맨드면_상품이_저장된다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productService.register(상품_등록_커맨드(brandId = brand.id))

            val saved = productJpaRepository.findById(product.id).orElseThrow()
            assertThat(saved.brandId).isEqualTo(brand.id)
            assertThat(saved.productName).isEqualTo("기본 상품")
            assertThat(saved.price).isEqualTo(10_000)
        }

        @Test
        fun `상품을_수정하면_변경된_값이_저장된다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productService.register(상품_등록_커맨드(brandId = brand.id))

            productService.update(
                productId = product.id,
                command = 상품_수정_커맨드(name = "수정 상품", price = 20_000),
            )

            val saved = productJpaRepository.findById(product.id).orElseThrow()
            assertThat(saved.productName).isEqualTo("수정 상품")
            assertThat(saved.price).isEqualTo(20_000)
        }

        @Test
        fun `상품을_삭제하면_조회가_불가하다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productService.register(상품_등록_커맨드(brandId = brand.id))

            productService.softDelete(product.id)

            val ex = assertThrows<CoreException> {
                productService.findById(product.id)
            }
            assertThat(ex.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @Test
        fun `상품_목록은_삭제된_상품을_제외하고_최신순으로_조회한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val first = productService.register(상품_등록_커맨드(brandId = brand.id, name = "첫 상품", price = 1_000))
            val second = productService.register(상품_등록_커맨드(brandId = brand.id, name = "둘째 상품", price = 2_000))
            val deleted = productService.register(상품_등록_커맨드(brandId = brand.id, name = "삭제 상품", price = 3_000))
            productService.softDelete(deleted.id)

            val products = productService.findProducts(ProductSearchCommand.of(brandId = brand.id))

            assertThat(products.map { it.id }).containsExactly(second.id, first.id)
        }

        @Test
        fun `상품_목록은_가격_낮은순으로_조회할_수_있다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val expensive = productService.register(상품_등록_커맨드(brandId = brand.id, name = "비싼 상품", price = 3_000))
            val cheap = productService.register(상품_등록_커맨드(brandId = brand.id, name = "싼 상품", price = 1_000))
            val middle = productService.register(상품_등록_커맨드(brandId = brand.id, name = "중간 상품", price = 2_000))

            val products = productService.findProducts(ProductSearchCommand.of(brandId = brand.id, sort = "price_asc"))

            assertThat(products.map { it.id }).containsExactly(cheap.id, middle.id, expensive.id)
        }

        @Test
        fun `상품_목록은_페이지_조건을_적용한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val first = productService.register(상품_등록_커맨드(brandId = brand.id, name = "첫 상품", price = 1_000))
            productService.register(상품_등록_커맨드(brandId = brand.id, name = "둘째 상품", price = 2_000))
            productService.register(상품_등록_커맨드(brandId = brand.id, name = "셋째 상품", price = 3_000))

            val products = productService.findProducts(ProductSearchCommand.of(brandId = brand.id, page = 1, size = 2))

            assertThat(products.map { it.id }).containsExactly(first.id)
        }

        @Test
        fun `상품_목록은_좋아요_많은순으로_조회할_수_있다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val low = productService.register(상품_등록_커맨드(brandId = brand.id, name = "낮은 상품", price = 1_000))
            val high = productService.register(상품_등록_커맨드(brandId = brand.id, name = "높은 상품", price = 2_000))
            val middle = productService.register(상품_등록_커맨드(brandId = brand.id, name = "중간 상품", price = 3_000))
            likeService.like(userId = 1L, productId = low.id)
            likeService.like(userId = 1L, productId = high.id)
            likeService.like(userId = 2L, productId = high.id)
            likeService.like(userId = 3L, productId = high.id)
            likeService.like(userId = 1L, productId = middle.id)
            likeService.like(userId = 2L, productId = middle.id)

            val products = productService.findProducts(
                ProductSearchCommand.of(brandId = brand.id, sort = "likes_desc", page = 0, size = 2),
            )

            assertThat(products.map { it.id }).containsExactly(high.id, middle.id)
        }

        @Test
        fun `상품_목록은_삭제된_브랜드의_상품을_제외한다`() {
            val activeBrand = brandService.register(브랜드_등록_커맨드(name = "활성 브랜드"))
            val deletedBrand = brandService.register(브랜드_등록_커맨드(name = "삭제 브랜드"))
            val activeProduct = productService.register(
                상품_등록_커맨드(brandId = activeBrand.id, name = "노출 상품", price = 1_000),
            )
            productService.register(
                상품_등록_커맨드(brandId = deletedBrand.id, name = "비노출 상품", price = 2_000),
            )
            brandService.softDelete(deletedBrand.id)

            val products = productService.findProducts(ProductSearchCommand.of())

            assertThat(products.map { it.id }).containsExactly(activeProduct.id)
        }
    }
