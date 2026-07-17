package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.like.infrastructure.persistence.ProductLikeCountJpaEntity
import com.loopers.domain.like.infrastructure.persistence.ProductLikeCountJpaRepository
import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.product.model.ProductSaleType
import com.loopers.domain.product.presentation.response.ProductResponse
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.ranking.vo.RankingKey
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class ProductApiE2ETest
    @Autowired
    constructor(
        private val brandService: BrandService,
        private val productLikeCountJpaRepository: ProductLikeCountJpaRepository,
        private val productService: ProductService,
        private val redissonClient: RedissonClient,
    ) : ApiTest() {
        companion object {
            private const val ENDPOINT = "/api/v1/products"
        }

        private val productResponseType =
            object : ParameterizedTypeReference<ApiResponse<ProductResponse>>() {}
        private val productListResponseType =
            object : ParameterizedTypeReference<ApiResponse<List<ProductResponse>>>() {}

        @Test
        fun `존재하는_상품_ID면_projection_좋아요_수와_상품_상세를_반환한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productService.register(상품_등록_커맨드(brandId = brand.id))
            productLikeCountJpaRepository.saveAndFlush(
                ProductLikeCountJpaEntity(productId = product.id, likeCount = 2L),
            )

            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.id).isEqualTo(product.id)
            assertThat(response.body?.data?.name).isEqualTo("기본 상품")
            assertThat(response.body?.data?.price).isEqualTo(10_000)
            assertThat(response.body?.data?.brandName).isEqualTo("기본 브랜드")
            assertThat(response.body?.data?.likeCount).isEqualTo(2L)
        }

        @Test
        fun `선착순_상품_상세는_대기열_진입_판단을_위해_LIMITED_saleType을_반환한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productService.register(
                상품_등록_커맨드(brandId = brand.id, saleType = ProductSaleType.LIMITED),
            )

            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.saleType).isEqualTo("LIMITED")
        }

        @Test
        fun `존재하지_않는_상품_ID면_404_NOT_FOUND를_반환한다`() {
            val response = testRestTemplate.exchange(
                "$ENDPOINT/999999",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }

        @Test
        fun `상품_목록은_브랜드로_필터링하고_최신순으로_반환한다`() {
            val targetBrand = brandService.register(브랜드_등록_커맨드())
            val otherBrand = brandService.register(브랜드_등록_커맨드(name = "다른 브랜드"))
            productService.register(상품_등록_커맨드(brandId = otherBrand.id, name = "다른 브랜드 상품", price = 1_000))
            val first = productService.register(상품_등록_커맨드(brandId = targetBrand.id, name = "첫 상품", price = 2_000))
            val second = productService.register(상품_등록_커맨드(brandId = targetBrand.id, name = "둘째 상품", price = 3_000))

            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=${targetBrand.id}",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productListResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.map { it.id }).containsExactly(second.id, first.id)
            assertThat(response.body?.data?.map { it.brandName }).containsExactly("기본 브랜드", "기본 브랜드")
        }

        @Test
        fun `상품_목록은_가격_낮은순과_페이지_조건을_적용한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            productService.register(상품_등록_커맨드(brandId = brand.id, name = "비싼 상품", price = 3_000))
            val cheap = productService.register(상품_등록_커맨드(brandId = brand.id, name = "싼 상품", price = 1_000))
            productService.register(상품_등록_커맨드(brandId = brand.id, name = "중간 상품", price = 2_000))

            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=${brand.id}&sort=price_asc&page=0&size=1",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productListResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.map { it.id }).containsExactly(cheap.id)
            assertThat(response.body?.data?.map { it.brandName }).containsExactly("기본 브랜드")
        }

        @Test
        fun `상품_목록은_projection_좋아요_많은순으로_조회할_수_있다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val low = productService.register(상품_등록_커맨드(brandId = brand.id, name = "낮은 상품", price = 1_000))
            val high = productService.register(상품_등록_커맨드(brandId = brand.id, name = "높은 상품", price = 2_000))
            val middle = productService.register(상품_등록_커맨드(brandId = brand.id, name = "중간 상품", price = 3_000))
            productLikeCountJpaRepository.saveAllAndFlush(
                listOf(
                    ProductLikeCountJpaEntity(productId = low.id, likeCount = 1L),
                    ProductLikeCountJpaEntity(productId = high.id, likeCount = 3L),
                    ProductLikeCountJpaEntity(productId = middle.id, likeCount = 2L),
                ),
            )

            val response = testRestTemplate.exchange(
                "$ENDPOINT?brandId=${brand.id}&sort=likes_desc&page=0&size=2",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productListResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.map { it.id }).containsExactly(high.id, middle.id)
            assertThat(response.body?.data?.map { it.brandName }).containsExactly("기본 브랜드", "기본 브랜드")
            assertThat(response.body?.data?.map { it.likeCount }).containsExactly(3L, 2L)
        }

        @Test
        fun `지원하지_않는_상품_정렬조건이면_400_BAD_REQUEST를_반환한다`() {
            val response = testRestTemplate.exchange(
                "$ENDPOINT?sort=unknown",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productListResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `상품_목록_페이지_값이_유효하지_않으면_400_BAD_REQUEST를_반환한다`() {
            val pageResponse = testRestTemplate.exchange(
                "$ENDPOINT?page=-1",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productListResponseType,
            )
            val sizeResponse = testRestTemplate.exchange(
                "$ENDPOINT?size=0",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productListResponseType,
            )

            assertThat(pageResponse.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(sizeResponse.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `오늘_랭킹판에_있는_상품_상세는_1부터_시작하는_rank를_반환한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val second = productService.register(상품_등록_커맨드(brandId = brand.id, name = "이등 상품"))
            val first = productService.register(상품_등록_커맨드(brandId = brand.id, name = "일등 상품", price = 20_000))
            val rankingSet = redissonClient.getScoredSortedSet<String>(
                RankingKey.daily(LocalDate.now(RankingKey.ZONE)),
            )
            rankingSet.add(5.0, first.id.toString())
            rankingSet.add(3.0, second.id.toString())

            val response = testRestTemplate.exchange(
                "$ENDPOINT/${second.id}",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.rank).isEqualTo(2L)
        }

        @Test
        fun `오늘_랭킹판에_없는_상품_상세는_rank가_null이다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productService.register(상품_등록_커맨드(brandId = brand.id))

            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.rank).isNull()
        }

        @Test
        fun `삭제된_상품은_상세_조회에서_404_NOT_FOUND를_반환한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productService.register(상품_등록_커맨드(brandId = brand.id))
            productService.softDelete(product.id)

            val response = testRestTemplate.exchange(
                "$ENDPOINT/${product.id}",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                productResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        }
    }
