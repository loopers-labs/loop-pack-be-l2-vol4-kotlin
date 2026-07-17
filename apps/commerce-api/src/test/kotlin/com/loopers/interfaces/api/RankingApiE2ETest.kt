package com.loopers.interfaces.api

import com.loopers.ApiTest
import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.product.support.ProductSteps.Companion.상품_등록_커맨드
import com.loopers.domain.ranking.infrastructure.persistence.ProductRankingDailyJpaEntity
import com.loopers.domain.ranking.infrastructure.persistence.ProductRankingDailyJpaId
import com.loopers.domain.ranking.infrastructure.persistence.ProductRankingDailyJpaRepository
import com.loopers.domain.ranking.presentation.response.RankingResponse
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

class RankingApiE2ETest
    @Autowired
    constructor(
        private val brandService: BrandService,
        private val productService: ProductService,
        private val productRankingDailyJpaRepository: ProductRankingDailyJpaRepository,
        private val redissonClient: RedissonClient,
    ) : ApiTest() {
        companion object {
            private const val ENDPOINT = "/api/v1/rankings"
        }

        private val rankingPageResponseType =
            object : ParameterizedTypeReference<ApiResponse<PageResponse<RankingResponse>>>() {}

        @Test
        fun `오늘_랭킹은_점수_내림차순으로_상품정보를_조합해_반환한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val first = productService.register(상품_등록_커맨드(brandId = brand.id, name = "일등 상품", price = 1_000))
            val second = productService.register(상품_등록_커맨드(brandId = brand.id, name = "이등 상품", price = 2_000))
            val third = productService.register(상품_등록_커맨드(brandId = brand.id, name = "삼등 상품", price = 3_000))
            seedTodayRanking(first.id to 5.0, second.id to 4.0, third.id to 1.0)

            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                rankingPageResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            val content = response.body?.data?.content
            assertThat(content?.map { it.productId }).containsExactly(first.id, second.id, third.id)
            assertThat(content?.map { it.rank }).containsExactly(1L, 2L, 3L)
            assertThat(content?.map { it.name }).containsExactly("일등 상품", "이등 상품", "삼등 상품")
            assertThat(content?.map { it.brandName }).containsExactly("기본 브랜드", "기본 브랜드", "기본 브랜드")
            assertThat(response.body?.data?.totalElements).isEqualTo(3L)
        }

        @Test
        fun `랭킹_페이지_조건을_적용하고_rank는_전체_순위를_유지한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val first = productService.register(상품_등록_커맨드(brandId = brand.id, name = "일등 상품", price = 1_000))
            val second = productService.register(상품_등록_커맨드(brandId = brand.id, name = "이등 상품", price = 2_000))
            val third = productService.register(상품_등록_커맨드(brandId = brand.id, name = "삼등 상품", price = 3_000))
            seedTodayRanking(first.id to 5.0, second.id to 4.0, third.id to 1.0)

            val response = testRestTemplate.exchange(
                "$ENDPOINT?page=1&size=2",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                rankingPageResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            val content = response.body?.data?.content
            assertThat(content?.map { it.productId }).containsExactly(third.id)
            assertThat(content?.map { it.rank }).containsExactly(3L)
            assertThat(response.body?.data?.totalElements).isEqualTo(3L)
            assertThat(response.body?.data?.hasNext).isFalse()
        }

        @Test
        fun `빈_랭킹판이면_빈_목록을_반환한다`() {
            val response = testRestTemplate.exchange(
                ENDPOINT,
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                rankingPageResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            assertThat(response.body?.data?.content).isEmpty()
            assertThat(response.body?.data?.totalElements).isZero()
        }

        @Test
        fun `잘못된_날짜_형식이면_400_BAD_REQUEST를_반환한다`() {
            val response = testRestTemplate.exchange(
                "$ENDPOINT?date=2026-07-17",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                rankingPageResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `랭킹_페이지_값이_유효하지_않으면_400_BAD_REQUEST를_반환한다`() {
            val pageResponse = testRestTemplate.exchange(
                "$ENDPOINT?page=-1",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                rankingPageResponseType,
            )
            val sizeResponse = testRestTemplate.exchange(
                "$ENDPOINT?size=0",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                rankingPageResponseType,
            )

            assertThat(pageResponse.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(sizeResponse.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }

        @Test
        fun `이틀_이전_날짜는_RDB_스냅샷으로_조회한다`() {
            val brand = brandService.register(브랜드_등록_커맨드())
            val product = productService.register(상품_등록_커맨드(brandId = brand.id, name = "과거 일등 상품"))
            val pastDate = LocalDate.now(RankingKey.ZONE).minusDays(3)
            productRankingDailyJpaRepository.saveAndFlush(
                ProductRankingDailyJpaEntity(
                    id = ProductRankingDailyJpaId(rankingDate = pastDate, productId = product.id),
                    rankNo = 1,
                    score = 5.0,
                ),
            )

            val response = testRestTemplate.exchange(
                "$ENDPOINT?date=${pastDate.format(RankingKey.DATE_FORMATTER)}",
                HttpMethod.GET,
                HttpEntity<Any>(Unit),
                rankingPageResponseType,
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
            val content = response.body?.data?.content
            assertThat(content?.map { it.productId }).containsExactly(product.id)
            assertThat(content?.map { it.rank }).containsExactly(1L)
            assertThat(content?.map { it.name }).containsExactly("과거 일등 상품")
        }

        private fun seedTodayRanking(vararg scores: Pair<Long, Double>) {
            val rankingSet = redissonClient.getScoredSortedSet<String>(
                RankingKey.daily(LocalDate.now(RankingKey.ZONE)),
            )
            scores.forEach { (productId, score) -> rankingSet.add(score, productId.toString()) }
        }
    }
