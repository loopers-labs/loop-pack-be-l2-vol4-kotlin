package com.loopers.interfaces.api.ranking

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.error.ErrorType
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
        databaseCleanUp.truncateAllTables()
    }

    private val today: String = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))

    @DisplayName("GET /api/v1/rankings — 오늘 일간 랭킹이 상품정보와 함께 점수순으로 반환된다.")
    @Test
    fun returnsDailyRankings() {
        val brand = brandRepository.save(BrandModel(name = "Nike", description = "d"))
        val top = productRepository.save(ProductModel(brandId = brand.id, name = "top", description = "d", price = BigDecimal("10000.00")))
        val second = productRepository.save(ProductModel(brandId = brand.id, name = "second", description = "d", price = BigDecimal("20000.00")))
        redisTemplate.opsForZSet().add("ranking:all:v1:$today", "${top.id}", 9.0)
        redisTemplate.opsForZSet().add("ranking:all:v1:$today", "${second.id}", 4.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?size=20&page=1",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val data = response.body!!.data!!
        assertThat(data.date).isEqualTo(today)
        assertThat(data.items).hasSize(2)
        assertThat(data.items[0].rank).isEqualTo(1L)
        assertThat(data.items[0].name).isEqualTo("top")
        assertThat(data.items[0].brandName).isEqualTo("Nike")
        assertThat(data.totalCount).isEqualTo(2L)
    }

    @DisplayName("이전 날짜 date 파라미터로 어제 랭킹을 조회할 수 있다.")
    @Test
    fun returnsPastDateRankings() {
        val brand = brandRepository.save(BrandModel(name = "Nike", description = "d"))
        val p = productRepository.save(ProductModel(brandId = brand.id, name = "yesterday", description = "d", price = BigDecimal("10000.00")))
        val yesterday = ZonedDateTime.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        redisTemplate.opsForZSet().add("ranking:all:v1:$yesterday", "${p.id}", 1.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?date=$yesterday",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.items.map { it.productId }).containsExactly(p.id)
    }

    @DisplayName("period=HOURLY는 시간 키를 조회한다.")
    @Test
    fun returnsHourlyRankings() {
        val brand = brandRepository.save(BrandModel(name = "Nike", description = "d"))
        val p = productRepository.save(ProductModel(brandId = brand.id, name = "hot", description = "d", price = BigDecimal("10000.00")))
        val hour = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHH"))
        redisTemplate.opsForZSet().add("ranking:hourly:v1:$hour", "${p.id}", 2.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?period=HOURLY",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.items.map { it.productId }).containsExactly(p.id)
    }

    @DisplayName("잘못된 date/period는 400 BAD_REQUEST를 반환한다.")
    @Test
    fun rejectsInvalidParams() {
        val badDate = testRestTemplate.exchange(
            "/api/v1/rankings?date=2026-07-17",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
        assertThat(badDate.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(badDate.body!!.meta.errorCode).isEqualTo(ErrorType.BAD_REQUEST.code)

        val badPeriod = testRestTemplate.exchange(
            "/api/v1/rankings?period=WEEKLY",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
        assertThat(badPeriod.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }
}
