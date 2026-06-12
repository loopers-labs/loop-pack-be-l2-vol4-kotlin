package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.product.presentation.response.ProductResponse
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class ProductApiE2ETest
    @Autowired
    constructor(
        private val brandService: BrandService,
        private val likeService: LikeService,
        private val productService: ProductService,
    ) : ApiTest() {
        companion object {
            private const val ENDPOINT = "/api/v1/products"
        }

        private val productResponseType =
            object : ParameterizedTypeReference<ApiResponse<ProductResponse>>() {}
        private val productListResponseType =
            object : ParameterizedTypeReference<ApiResponse<List<ProductResponse>>>() {}

        @Test
        fun `존재하는_상품_ID면_상품_상세를_반환한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productService.register(상품_등록_커맨드(brandId = brand.id))
            likeService.initializeCount(product.id)
            likeService.like(userId = 1L, productId = product.id)
            likeService.like(userId = 2L, productId = product.id)

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
        fun `상품_목록은_좋아요_많은순으로_조회할_수_있다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val low = productService.register(상품_등록_커맨드(brandId = brand.id, name = "낮은 상품", price = 1_000))
            val high = productService.register(상품_등록_커맨드(brandId = brand.id, name = "높은 상품", price = 2_000))
            val middle = productService.register(상품_등록_커맨드(brandId = brand.id, name = "중간 상품", price = 3_000))
            likeService.initializeCount(low.id)
            likeService.initializeCount(high.id)
            likeService.initializeCount(middle.id)
            likeService.like(userId = 1L, productId = low.id)
            likeService.like(userId = 1L, productId = high.id)
            likeService.like(userId = 2L, productId = high.id)
            likeService.like(userId = 3L, productId = high.id)
            likeService.like(userId = 1L, productId = middle.id)
            likeService.like(userId = 2L, productId = middle.id)

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
