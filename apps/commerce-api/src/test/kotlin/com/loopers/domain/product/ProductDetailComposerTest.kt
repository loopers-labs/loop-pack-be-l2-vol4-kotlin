package com.loopers.domain.product

import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandRepositoryPort
import com.loopers.domain.stock.Stock
import com.loopers.domain.stock.StockRepositoryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ProductDetailComposerTest {

    private lateinit var brandRepositoryPort: BrandRepositoryPort
    private lateinit var stockRepositoryPort: StockRepositoryPort
    private lateinit var likeCountQueryPort: LikeCountQueryPort
    private lateinit var composer: ProductDetailComposer

    @BeforeEach
    fun setUp() {
        brandRepositoryPort = mockk()
        stockRepositoryPort = mockk()
        likeCountQueryPort = mockk()
        composer = ProductDetailComposer(brandRepositoryPort, stockRepositoryPort, likeCountQueryPort)
    }

    @DisplayName("compose 호출 시, ")
    @Nested
    inner class Compose {
        @DisplayName("Brand/Stock/LikeCount를 조합해 ProductDetail을 반환한다.")
        @Test
        fun returnsProductDetail() {
            val product = Product(id = 1L, name = "에어맥스", price = 100L, description = "d", brandId = 7L)
            every { brandRepositoryPort.findById(7L) } returns Brand(id = 7L, name = "Nike", description = "x")
            every { stockRepositoryPort.findByProductId(1L) } returns Stock(id = 11L, productId = 1L, quantity = 30)
            every { likeCountQueryPort.countByProductId(1L) } returns 42L

            val detail = composer.compose(product)

            assertThat(detail.id).isEqualTo(1L)
            assertThat(detail.brandName).isEqualTo("Nike")
            assertThat(detail.stockQuantity).isEqualTo(30)
            assertThat(detail.likeCount).isEqualTo(42L)
        }

        @DisplayName("Brand가 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenBrandMissing() {
            val product = Product(id = 1L, name = "p", price = 100L, description = "d", brandId = 7L)
            every { brandRepositoryPort.findById(7L) } returns null
            every { stockRepositoryPort.findByProductId(1L) } returns Stock(id = 11L, productId = 1L, quantity = 30)
            every { likeCountQueryPort.countByProductId(1L) } returns 0L

            val result = assertThrows<CoreException> { composer.compose(product) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }

        @DisplayName("Stock이 없으면 NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFound_whenStockMissing() {
            val product = Product(id = 1L, name = "p", price = 100L, description = "d", brandId = 7L)
            every { brandRepositoryPort.findById(7L) } returns Brand(id = 7L, name = "Nike", description = "x")
            every { stockRepositoryPort.findByProductId(1L) } returns null

            val result = assertThrows<CoreException> { composer.compose(product) }
            assertThat(result.errorType).isEqualTo(ErrorType.NOT_FOUND)
        }
    }

    @DisplayName("composeAll 호출 시, ")
    @Nested
    inner class ComposeAll {
        @DisplayName("빈 입력은 빈 리스트를 반환한다.")
        @Test
        fun returnsEmpty_whenInputEmpty() {
            assertThat(composer.composeAll(emptyList())).isEmpty()
        }

        @DisplayName("Brand/Stock/LikeCount를 한 번씩 batch 조회해 summary 리스트를 만든다.")
        @Test
        fun composesBatch() {
            val p1 = Product(id = 1L, name = "n1", price = 100L, description = "d", brandId = 7L)
            val p2 = Product(id = 2L, name = "n2", price = 200L, description = "d", brandId = 8L)
            every { brandRepositoryPort.findAllByIds(match { it.toSet() == setOf(7L, 8L) }) } returns listOf(
                Brand(id = 7L, name = "Nike", description = "x"),
                Brand(id = 8L, name = "Adidas", description = "y"),
            )
            every { stockRepositoryPort.findAllByProductIdIn(listOf(1L, 2L)) } returns listOf(
                Stock(id = 11L, productId = 1L, quantity = 10),
                Stock(id = 12L, productId = 2L, quantity = 20),
            )
            every { likeCountQueryPort.countsByProductIds(listOf(1L, 2L)) } returns mapOf(1L to 5L, 2L to 0L)

            val summaries = composer.composeAll(listOf(p1, p2))

            val byId = summaries.associateBy { it.id }
            assertThat(byId[1L]?.brandName).isEqualTo("Nike")
            assertThat(byId[1L]?.stockQuantity).isEqualTo(10)
            assertThat(byId[1L]?.likeCount).isEqualTo(5L)
            assertThat(byId[2L]?.brandName).isEqualTo("Adidas")
            assertThat(byId[2L]?.likeCount).isEqualTo(0L)
        }

        @DisplayName("LikeCount 맵에 productId가 빠져 있으면 0으로 채운다.")
        @Test
        fun defaultsLikeCountToZero_whenMissing() {
            val p = Product(id = 1L, name = "n", price = 100L, description = "d", brandId = 7L)
            every { brandRepositoryPort.findAllByIds(listOf(7L)) } returns listOf(Brand(id = 7L, name = "Nike", description = "x"))
            every { stockRepositoryPort.findAllByProductIdIn(listOf(1L)) } returns listOf(Stock(id = 11L, productId = 1L, quantity = 10))
            every { likeCountQueryPort.countsByProductIds(listOf(1L)) } returns emptyMap()

            val summaries = composer.composeAll(listOf(p))

            assertThat(summaries.single().likeCount).isEqualTo(0L)
        }
    }
}
