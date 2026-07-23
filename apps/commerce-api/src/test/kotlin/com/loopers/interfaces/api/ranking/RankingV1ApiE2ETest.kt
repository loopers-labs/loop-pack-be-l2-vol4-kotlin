package com.loopers.interfaces.api.ranking

import com.loopers.config.redis.RankingRedisKeys
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.infrastructure.brand.entity.BrandEntity
import com.loopers.infrastructure.brand.repository.BrandJpaRepository
import com.loopers.infrastructure.product.entity.ProductEntity
import com.loopers.infrastructure.product.entity.ProductStatEntity
import com.loopers.infrastructure.product.repository.ProductJpaRepository
import com.loopers.infrastructure.product.repository.ProductStatJpaRepository
import com.loopers.infrastructure.ranking.ProductRankMonthlyReadJpaRepository
import com.loopers.infrastructure.ranking.ProductRankPublicationJpaRepository
import com.loopers.infrastructure.ranking.ProductRankWeeklyReadJpaRepository
import com.loopers.infrastructure.ranking.entity.ProductRankMonthlyEntity
import com.loopers.infrastructure.ranking.entity.ProductRankPublicationEntity
import com.loopers.infrastructure.ranking.entity.ProductRankWeeklyEntity
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.PageResponse
import com.loopers.interfaces.api.product.dto.ProductV1Dto
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandJpaRepository: BrandJpaRepository,
    private val productJpaRepository: ProductJpaRepository,
    private val productStatJpaRepository: ProductStatJpaRepository,
    private val productRankPublicationJpaRepository: ProductRankPublicationJpaRepository,
    private val productRankWeeklyReadJpaRepository: ProductRankWeeklyReadJpaRepository,
    private val productRankMonthlyReadJpaRepository: ProductRankMonthlyReadJpaRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("명시한 날짜의 랭킹을 Redis 순서와 1-based rank로 페이지 조회한다")
    @Test
    fun returnsRankingPageForRequestedDate() {
        val date = LocalDate.of(2026, 7, 13)
        val brand = createBrand("loopers")
        val first = createProduct(brand.id, "rank-first", likeCount = 7L)
        val second = createProduct(brand.id, "rank-second", likeCount = 5L)
        val third = createProduct(brand.id, "rank-third", likeCount = 3L)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(date), third.id.toString(), 10.0)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(date), first.id.toString(), 30.0)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(date), second.id.toString(), 20.0)

        val response = getRankings(date = date, page = 0, size = 2)

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.data).hasSize(2) },
            { assertThat(response.body?.data?.data?.map { it.productId }).containsExactly(first.id, second.id) },
            { assertThat(response.body?.data?.data?.map { it.rank }).containsExactly(1L, 2L) },
            { assertThat(response.body?.data?.data?.map { it.score }).containsExactly(30.0, 20.0) },
            { assertThat(response.body?.data?.data?.first()?.brandName).isEqualTo(brand.name) },
            { assertThat(response.body?.data?.data?.first()?.likeCount).isEqualTo(7L) },
            { assertThat(response.body?.data?.meta?.totalElements).isEqualTo(3L) },
            { assertThat(response.body?.data?.meta?.totalPages).isEqualTo(2) },
        )
    }

    @DisplayName("랭킹 key가 없으면 빈 페이지를 반환한다")
    @Test
    fun returnsEmptyPageWhenRankingDoesNotExist() {
        val response = getRankings(date = LocalDate.of(2026, 7, 12), page = 0, size = 20)

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.data).isEmpty() },
            { assertThat(response.body?.data?.meta?.totalElements).isZero() },
        )
    }

    @DisplayName("period를 생략하면 Daily 랭킹 응답과 동일하다")
    @Test
    fun defaultsToDailyRankingWhenPeriodIsOmitted() {
        val date = LocalDate.of(2026, 7, 13)
        val brand = createBrand("default-daily")
        val product = createProduct(brand.id, "default-daily-product", likeCount = 1L)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(date), product.id.toString(), 15.0)

        val defaultResponse = getRankingsWithoutPeriod(date = date, page = 0, size = 20)
        val dailyResponse = getRankings(date = date, period = RankingPeriod.DAILY, page = 0, size = 20)

        assertAll(
            { assertThat(defaultResponse.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(defaultResponse.body?.data?.data?.map { it.productId }).isEqualTo(dailyResponse.body?.data?.data?.map { it.productId }) },
            { assertThat(defaultResponse.body?.data?.data?.map { it.rank }).isEqualTo(dailyResponse.body?.data?.data?.map { it.rank }) },
            { assertThat(defaultResponse.body?.data?.meta?.totalElements).isEqualTo(dailyResponse.body?.data?.meta?.totalElements) },
        )
    }

    @DisplayName("Weekly 랭킹은 월 경계 날짜가 같은 월요일 baseDate로 정규화되고 8일 TTL로 캐시된다")
    @Test
    fun normalizesWeeklyRankingAndCachesWithTtl() {
        val requestedSaturday = LocalDate.of(2026, 8, 1)
        val previousMonday = LocalDate.of(2026, 7, 27)
        val generationId = "weekly-generation-20260727"
        val brand = createBrand("weekly-rank")
        val first = createProduct(brand.id, "weekly-first", likeCount = 10L)
        val second = createProduct(brand.id, "weekly-second", likeCount = 8L)
        productRankPublicationJpaRepository.save(
            ProductRankPublicationEntity(
                period = RankingPeriod.WEEKLY.name,
                baseDate = previousMonday,
                generationId = generationId,
                publishedAt = ZonedDateTime.now(),
            ),
        )
        productRankWeeklyReadJpaRepository.saveAll(
            listOf(
                ProductRankWeeklyEntity(previousMonday, second.id, 20.0),
                ProductRankWeeklyEntity(previousMonday, first.id, 30.0),
            ),
        )

        val response = getRankings(
            date = requestedSaturday,
            period = RankingPeriod.WEEKLY,
            page = 0,
            size = 20,
        )

        val cacheKey = RankingRedisKeys.weekly(previousMonday, generationId)
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.data?.map { it.productId }).containsExactly(first.id, second.id) },
            { assertThat(response.body?.data?.data?.map { it.rank }).containsExactly(1L, 2L) },
            { assertThat(response.body?.data?.meta?.totalElements).isEqualTo(2L) },
            { assertThat(redisTemplate.opsForZSet().zCard(cacheKey) ?: -1L).isEqualTo(2L) },
            { assertThat(redisTemplate.getExpire(cacheKey)).isBetween(Duration.ofDays(7).seconds, Duration.ofDays(8).seconds) },
            { assertThat(redisTemplate.hasKey(RankingRedisKeys.weekly(requestedSaturday))).isFalse() },
        )
    }

    @DisplayName("Weekly cache hit에서는 MV row가 없어도 Redis에서 조회한다")
    @Test
    fun readsWeeklyRankingFromCacheWhenCacheExists() {
        val baseDate = LocalDate.of(2026, 7, 27)
        val generationId = "weekly-cache-hit"
        val brand = createBrand("weekly-cache")
        val product = createProduct(brand.id, "weekly-cache-product", likeCount = 3L)
        productRankPublicationJpaRepository.save(
            ProductRankPublicationEntity(
                period = RankingPeriod.WEEKLY.name,
                baseDate = baseDate,
                generationId = generationId,
                publishedAt = ZonedDateTime.now(),
            ),
        )
        redisTemplate.opsForZSet().add(RankingRedisKeys.weekly(baseDate, generationId), product.id.toString(), 77.0)

        val response = getRankings(
            date = baseDate.plusDays(2),
            period = RankingPeriod.WEEKLY,
            page = 0,
            size = 20,
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.data?.map { it.productId }).containsExactly(product.id) },
            { assertThat(response.body?.data?.data?.map { it.score }).containsExactly(77.0) },
            { assertThat(productRankWeeklyReadJpaRepository.findTop100ByBaseDateOrderByRankingScoreDescProductIdAsc(baseDate)).isEmpty() },
        )
    }

    @DisplayName("Monthly 랭킹은 모든 날짜가 월 1일 baseDate로 정규화되고 32일 TTL로 캐시된다")
    @Test
    fun normalizesMonthlyRankingAndCachesWithTtl() {
        val baseDate = LocalDate.of(2026, 8, 1)
        val generationId = "monthly-generation-20260801"
        val brand = createBrand("monthly-rank")
        val first = createProduct(brand.id, "monthly-first", likeCount = 10L)
        val second = createProduct(brand.id, "monthly-second", likeCount = 8L)
        productRankPublicationJpaRepository.save(
            ProductRankPublicationEntity(
                period = RankingPeriod.MONTHLY.name,
                baseDate = baseDate,
                generationId = generationId,
                publishedAt = ZonedDateTime.now(),
            ),
        )
        productRankMonthlyReadJpaRepository.saveAll(
            listOf(
                ProductRankMonthlyEntity(baseDate, second.id, 20.0),
                ProductRankMonthlyEntity(baseDate, first.id, 30.0),
            ),
        )

        val response = getRankings(
            date = LocalDate.of(2026, 8, 31),
            period = RankingPeriod.MONTHLY,
            page = 0,
            size = 20,
        )

        val cacheKey = RankingRedisKeys.monthly(baseDate, generationId)
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.data?.map { it.productId }).containsExactly(first.id, second.id) },
            { assertThat(response.body?.data?.data?.map { it.rank }).containsExactly(1L, 2L) },
            { assertThat(response.body?.data?.meta?.totalElements).isEqualTo(2L) },
            { assertThat(redisTemplate.opsForZSet().zCard(cacheKey) ?: -1L).isEqualTo(2L) },
            { assertThat(redisTemplate.getExpire(cacheKey)).isBetween(Duration.ofDays(31).seconds, Duration.ofDays(32).seconds) },
        )
    }

    @DisplayName("발행된 기간 랭킹의 MV가 비어 있으면 빈 결과를 반환하고 캐시하지 않는다")
    @Test
    fun returnsEmptyPageAndDoesNotCacheWhenPublishedMvIsEmpty() {
        val baseDate = LocalDate.of(2026, 8, 1)
        val generationId = "monthly-empty-mv"
        productRankPublicationJpaRepository.save(
            ProductRankPublicationEntity(
                period = RankingPeriod.MONTHLY.name,
                baseDate = baseDate,
                generationId = generationId,
                publishedAt = ZonedDateTime.now(),
            ),
        )

        val response = getRankings(
            date = baseDate.plusDays(10),
            period = RankingPeriod.MONTHLY,
            page = 0,
            size = 20,
        )

        val cacheKey = RankingRedisKeys.monthly(baseDate, generationId)
        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.data).isEmpty() },
            { assertThat(response.body?.data?.meta?.totalElements).isZero() },
            { assertThat(redisTemplate.hasKey(cacheKey)).isFalse() },
        )
    }

    @DisplayName("Weekly 랭킹은 발행된 주차가 없으면 빈 페이지를 반환하고 캐시하지 않는다")
    @Test
    fun returnsEmptyWeeklyPageWhenNothingWasPublished() {
        val requestedMonday = LocalDate.of(2026, 8, 3)

        val response = getRankings(
            date = requestedMonday,
            period = RankingPeriod.WEEKLY,
            page = 0,
            size = 20,
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.data).isEmpty() },
            { assertThat(response.body?.data?.meta?.totalElements).isZero() },
            { assertThat(redisTemplate.hasKey(RankingRedisKeys.weekly(requestedMonday))).isFalse() },
        )
    }

    @DisplayName("랭킹 API와 상품 상세는 같은 오늘의 1-based rank를 반환한다")
    @Test
    fun returnsSameRankFromRankingAndProductDetail() {
        val today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))
        val brand = createBrand("rank-consistency")
        val product = createProduct(brand.id, "ranked-product", likeCount = 1L)
        redisTemplate.opsForZSet().add(RankingRedisKeys.all(today), product.id.toString(), 12.3)

        val rankingResponse = getRankings(date = today, page = 0, size = 20)
        val detailResponse = testRestTemplate.exchange(
            "/api/v1/products/${product.id}",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>>() {},
        )

        assertAll(
            { assertThat(rankingResponse.body?.data?.data?.single()?.rank).isEqualTo(1L) },
            { assertThat(detailResponse.body?.data?.rank).isEqualTo(1L) },
        )
    }

    private fun getRankings(
        date: LocalDate,
        period: RankingPeriod = RankingPeriod.DAILY,
        page: Int,
        size: Int,
    ) = testRestTemplate.exchange(
        "/api/v1/rankings?date=${date.format(DateTimeFormatter.BASIC_ISO_DATE)}&period=$period&page=$page&size=$size",
        HttpMethod.GET,
        null,
        object : ParameterizedTypeReference<ApiResponse<PageResponse<RankingResponse>>>() {},
    )

    private fun getRankingsWithoutPeriod(
        date: LocalDate,
        page: Int,
        size: Int,
    ) = testRestTemplate.exchange(
        "/api/v1/rankings?date=${date.format(DateTimeFormatter.BASIC_ISO_DATE)}&page=$page&size=$size",
        HttpMethod.GET,
        null,
        object : ParameterizedTypeReference<ApiResponse<PageResponse<RankingResponse>>>() {},
    )

    private fun createBrand(name: String): BrandEntity {
        return brandJpaRepository.save(
            BrandEntity(
                name = name,
                description = "$name brand",
                logoImageUrl = "https://image.loopers/$name.png",
            ),
        )
    }

    private fun createProduct(
        brandId: Long,
        name: String,
        likeCount: Long,
    ): ProductEntity {
        val product = productJpaRepository.save(
            ProductEntity(
                brandId = brandId,
                name = name,
                price = 10_000L,
                description = "$name product",
                imageUrl = "https://image.loopers/$name.png",
            ),
        )
        productStatJpaRepository.save(
            ProductStatEntity(
                productId = product.id,
                brandId = brandId,
                likeCount = likeCount,
            ),
        )
        return product
    }

    data class RankingResponse(
        val productId: Long,
        val productName: String,
        val price: Long,
        val imageUrl: String,
        val brandId: Long,
        val brandName: String,
        val likeCount: Long,
        val rank: Long,
        val score: Double,
    )
}
