package com.loopers.domain.product.unit

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_도메인_생성
import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.product.application.ProductFacade
import com.loopers.domain.product.application.command.ProductSearchCommand
import com.loopers.domain.product.application.info.ProductDetailInfo
import com.loopers.domain.product.application.info.ProductSummaryInfo
import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.product.application.service.StockService
import com.loopers.domain.product.support.ProductSteps.Companion.기본_상품_ID
import com.loopers.domain.product.support.ProductSteps.Companion.재고_도메인_생성
import com.loopers.domain.product.support.ProductSteps.Companion.상품_도메인_생성
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.ranking.port.ProductRankingReader
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.springframework.dao.DataAccessResourceFailureException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProductFacadeTest {
    @Test
    fun `상품_등록_결과를_Info로_반환한다`() {
        val brandService = mockk<BrandService>()
        val likeService = mockk<LikeService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val productRankingReader = mockk<ProductRankingReader>()
        val productFacade = ProductFacade(brandService, productService, stockService, likeService, productRankingReader)
        every { brandService.getById(10L) } returns 브랜드_도메인_생성(id = 10L)
        every { productService.register(상품_등록_커맨드()) } returns 상품_도메인_생성(id = 기본_상품_ID)
        every { stockService.initialize(기본_상품_ID, 10L) } returns 재고_도메인_생성(productId = 기본_상품_ID)
        every { likeService.initializeCount(기본_상품_ID) } returns Unit

        val info = productFacade.registerProduct(상품_등록_커맨드())

        assertThat(info.id).isEqualTo(기본_상품_ID)
        assertThat(info.name).isEqualTo("기본 상품")
        assertThat(info.price).isEqualTo(10_000)
    }

    @Test
    fun `상품_등록_시_초기_재고를_함께_생성한다`() {
        val brandService = mockk<BrandService>()
        val likeService = mockk<LikeService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val productRankingReader = mockk<ProductRankingReader>()
        val productFacade = ProductFacade(brandService, productService, stockService, likeService, productRankingReader)
        val command = 상품_등록_커맨드(initialStock = 15)
        every { brandService.getById(10L) } returns 브랜드_도메인_생성(id = 10L)
        every { productService.register(command) } returns 상품_도메인_생성(id = 기본_상품_ID)
        every { stockService.initialize(기본_상품_ID, 15) } returns 재고_도메인_생성(
            productId = 기본_상품_ID,
            leftStock = 15,
        )
        every { likeService.initializeCount(기본_상품_ID) } returns Unit

        productFacade.registerProduct(command)

        verifySequence {
            brandService.getById(10L)
            productService.register(command)
            stockService.initialize(기본_상품_ID, 15)
            likeService.initializeCount(기본_상품_ID)
        }
    }

    @Test
    fun `상품_상세_결과를_Info로_반환한다`() {
        val brandService = mockk<BrandService>()
        val likeService = mockk<LikeService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val productRankingReader = mockk<ProductRankingReader>()
        val productFacade = ProductFacade(brandService, productService, stockService, likeService, productRankingReader)
        every { productService.getById(기본_상품_ID) } returns 상품_도메인_생성(id = 기본_상품_ID)
        every { brandService.getById(10L) } returns 브랜드_도메인_생성(id = 10L)
        every { likeService.countByProductId(기본_상품_ID) } returns 3L
        every { productRankingReader.findRank(any(), 기본_상품_ID) } returns 2L

        val info = productFacade.getProduct(기본_상품_ID)

        assertThat(info).isInstanceOf(ProductDetailInfo::class.java)
        assertThat(info.id).isEqualTo(기본_상품_ID)
        assertThat(info.brandId).isEqualTo(10L)
        assertThat(info.brandName).isEqualTo("기본 브랜드")
        assertThat(info.likeCount).isEqualTo(3L)
        assertThat(info.rank).isEqualTo(2L)
    }

    @Test
    fun `랭킹판에_없는_상품_상세는_rank가_null이다`() {
        val brandService = mockk<BrandService>()
        val likeService = mockk<LikeService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val productRankingReader = mockk<ProductRankingReader>()
        val productFacade = ProductFacade(brandService, productService, stockService, likeService, productRankingReader)
        every { productService.getById(기본_상품_ID) } returns 상품_도메인_생성(id = 기본_상품_ID)
        every { brandService.getById(10L) } returns 브랜드_도메인_생성(id = 10L)
        every { likeService.countByProductId(기본_상품_ID) } returns 0L
        every { productRankingReader.findRank(any(), 기본_상품_ID) } returns null

        val info = productFacade.getProduct(기본_상품_ID)

        assertThat(info.rank).isNull()
    }

    @Test
    fun `랭킹_조회가_실패해도_상품_상세는_rank_null로_응답한다`() {
        val brandService = mockk<BrandService>()
        val likeService = mockk<LikeService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val productRankingReader = mockk<ProductRankingReader>()
        val productFacade = ProductFacade(brandService, productService, stockService, likeService, productRankingReader)
        every { productService.getById(기본_상품_ID) } returns 상품_도메인_생성(id = 기본_상품_ID)
        every { brandService.getById(10L) } returns 브랜드_도메인_생성(id = 10L)
        every { likeService.countByProductId(기본_상품_ID) } returns 0L
        every {
            productRankingReader.findRank(any(), 기본_상품_ID)
        } throws DataAccessResourceFailureException("redis down")

        val info = productFacade.getProduct(기본_상품_ID)

        assertThat(info.rank).isNull()
    }

    @Test
    fun `상품_목록_결과를_Info_목록으로_반환한다`() {
        val brandService = mockk<BrandService>()
        val likeService = mockk<LikeService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val productRankingReader = mockk<ProductRankingReader>()
        val productFacade = ProductFacade(brandService, productService, stockService, likeService, productRankingReader)
        val command = ProductSearchCommand(brandId = 10L)
        every { productService.findProducts(command) } returns listOf(
            상품_도메인_생성(id = 2L),
            상품_도메인_생성(id = 1L),
        )
        every { likeService.countByProductIds(setOf(2L, 1L)) } returns mapOf(2L to 3L)
        every { brandService.getByIds(setOf(10L)) } returns listOf(브랜드_도메인_생성(id = 10L))

        val infos = productFacade.findProducts(command)

        assertThat(infos.first()).isInstanceOf(ProductSummaryInfo::class.java)
        assertThat(infos.map { it.id }).containsExactly(2L, 1L)
        assertThat(infos.map { it.brandName }).containsExactly("기본 브랜드", "기본 브랜드")
        assertThat(infos.map { it.likeCount }).containsExactly(3L, 0L)
    }

    @Test
    fun `좋아요순_상품_목록은_서비스_조회_순서를_유지하고_좋아요_수를_조합한다`() {
        val brandService = mockk<BrandService>()
        val likeService = mockk<LikeService>()
        val productService = mockk<ProductService>()
        val stockService = mockk<StockService>()
        val productRankingReader = mockk<ProductRankingReader>()
        val productFacade = ProductFacade(brandService, productService, stockService, likeService, productRankingReader)
        val command = ProductSearchCommand.of(brandId = 10L, sort = "likes_desc", page = 0, size = 2)
        every { productService.findProducts(command) } returns listOf(
            상품_도메인_생성(id = 2L),
            상품_도메인_생성(id = 3L),
        )
        every { likeService.countByProductIds(setOf(2L, 3L)) } returns mapOf(
            2L to 3L,
            3L to 2L,
        )
        every { brandService.getByIds(setOf(10L)) } returns listOf(브랜드_도메인_생성(id = 10L))

        val infos = productFacade.findProducts(command)

        assertThat(infos.map { it.id }).containsExactly(2L, 3L)
        assertThat(infos.map { it.brandName }).containsExactly("기본 브랜드", "기본 브랜드")
        assertThat(infos.map { it.likeCount }).containsExactly(3L, 2L)
    }
}
