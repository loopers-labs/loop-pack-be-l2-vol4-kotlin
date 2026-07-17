package com.loopers.ranking.interfaces

import com.loopers.brand.application.BrandCreateCommand
import com.loopers.brand.application.BrandService
import com.loopers.product.application.ProductCreateCommand
import com.loopers.product.application.ProductService
import com.loopers.support.DatabaseCleanup
import com.loopers.utils.RedisCleanUp
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class RankingControllerTest @Autowired constructor(
    private val mockMvc: MockMvc,
    private val brandService: BrandService,
    private val productService: ProductService,
    private val redisTemplate: RedisTemplate<String, String>,
    private val databaseCleanup: DatabaseCleanup,
    private val redisCleanUp: RedisCleanUp,
) {
    @BeforeEach
    fun cleanup() {
        databaseCleanup.execute()
        redisCleanUp.truncateAll()
    }

    @DisplayName("GET /api/v1/rankings")
    @Nested
    inner class GetRankings {
        @DisplayName("date 를 지정하면 해당 날짜 판을 score 내림차순으로 반환하고, rank 는 1부터 매긴다.")
        @Test
        fun returnsRankingPageOrderedByScoreDesc() {
            val productIds = seedProducts(3)
            seedRanking(TODAY, productIds[0], "0.5")
            seedRanking(TODAY, productIds[1], "0.9")
            seedRanking(TODAY, productIds[2], "0.7")

            mockMvc.perform(get(RANKINGS_PATH).param(PARAM_DATE, TODAY.format(BASIC_ISO)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].rank").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(productIds[1]))
                .andExpect(jsonPath("$.data.items[0].score").value(0.9))
                .andExpect(jsonPath("$.data.items[0].name").value("product-1"))
                .andExpect(jsonPath("$.data.items[1].productId").value(productIds[2]))
                .andExpect(jsonPath("$.data.items[2].rank").value(3))
                .andExpect(jsonPath("$.data.items[2].productId").value(productIds[0]))
        }

        @DisplayName("date 를 지정하지 않으면 오늘(KST) 판을 반환한다.")
        @Test
        fun defaultsToTodayKst_whenDateOmitted() {
            val productIds = seedProducts(1)
            seedRanking(TODAY, productIds[0], "0.3")
            seedRanking(TODAY.minusDays(1), productIds[0], "9.9")

            mockMvc.perform(get(RANKINGS_PATH))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].score").value(0.3))
        }

        @DisplayName("해당 날짜 판이 비어 있으면 빈 배열을 반환한다.")
        @Test
        fun returnsEmptyItems_whenRankingEmpty() {
            mockMvc.perform(get(RANKINGS_PATH).param(PARAM_DATE, "20200101"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(0))
        }

        @DisplayName("size 범위 초과·date 포맷 오류·page 0 이면 400 BAD_REQUEST 를 반환한다.")
        @Test
        fun returnsBadRequest_whenParamsInvalid() {
            mockMvc.perform(get(RANKINGS_PATH).param(PARAM_SIZE, "0"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_CODE).value(CODE_INVALID_PAGE_SIZE))

            mockMvc.perform(get(RANKINGS_PATH).param(PARAM_SIZE, "101"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_CODE).value(CODE_INVALID_PAGE_SIZE))

            mockMvc.perform(get(RANKINGS_PATH).param(PARAM_DATE, "2026-07-15"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_CODE).value(CODE_INVALID_DATE_FORMAT))

            mockMvc.perform(get(RANKINGS_PATH).param(PARAM_PAGE, "0"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath(JSON_CODE).value(CODE_INVALID_PAGE))
        }

        @DisplayName("page 와 size 로 다음 페이지를 조회하면 rank 가 이어서 매겨진다.")
        @Test
        fun continuesRankAcrossPages() {
            val productIds = seedProducts(3)
            seedRanking(TODAY, productIds[0], "0.9")
            seedRanking(TODAY, productIds[1], "0.7")
            seedRanking(TODAY, productIds[2], "0.5")

            mockMvc.perform(
                get(RANKINGS_PATH).param(PARAM_DATE, TODAY.format(BASIC_ISO)).param(PARAM_PAGE, "2").param(PARAM_SIZE, "2"),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].rank").value(3))
                .andExpect(jsonPath("$.data.items[0].productId").value(productIds[2]))
        }

        @DisplayName("활성 상품이 없는 랭킹 행은 응답 항목에서 제외한다.")
        @Test
        fun excludesRow_whenActiveProductMissing() {
            val productIds = seedProducts(1)
            seedRanking(TODAY, productIds[0], "0.5")
            seedRanking(TODAY, 999_999L, "0.9")

            mockMvc.perform(get(RANKINGS_PATH).param(PARAM_DATE, TODAY.format(BASIC_ISO)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productId").value(productIds[0]))
        }
    }

    @DisplayName("GET /api/v1/products/{productId} — 순위 포함")
    @Nested
    inner class GetProductDetailRank {
        @DisplayName("오늘 랭킹에 진입한 상품이면 상세 응답에 rank 를 포함한다.")
        @Test
        fun includesRank_whenProductRankedToday() {
            val productIds = seedProducts(2)
            seedRanking(TODAY, productIds[0], "0.9")
            seedRanking(TODAY, productIds[1], "0.5")

            mockMvc.perform(get("$PRODUCTS_PATH/${productIds[1]}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.rank").value(2))
        }

        @DisplayName("오늘 랭킹에 없는 상품이면 rank 필드를 생략한다.")
        @Test
        fun omitsRank_whenProductNotRanked() {
            val productIds = seedProducts(1)

            mockMvc.perform(get("$PRODUCTS_PATH/${productIds[0]}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.rank").doesNotExist())
        }

        @DisplayName("score 동점이면 같은 rank 를 가진다 — competition ranking.")
        @Test
        fun sharesRank_whenScoresTied() {
            val productIds = seedProducts(3)
            seedRanking(TODAY, productIds[0], "0.9")
            seedRanking(TODAY, productIds[1], "0.7")
            seedRanking(TODAY, productIds[2], "0.7")

            mockMvc.perform(get("$PRODUCTS_PATH/${productIds[1]}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.rank").value(2))

            mockMvc.perform(get("$PRODUCTS_PATH/${productIds[2]}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.rank").value(2))
        }
    }

    private fun seedProducts(count: Int): List<Long> {
        val brandId = brandService.register(BrandCreateCommand(name = "brand-ranking")).id
        return (0 until count).map { i ->
            productService.register(
                ProductCreateCommand(brandId = brandId, name = "product-$i", price = (i + 1) * 1_000L, stock = 100),
            ).id
        }
    }

    private fun seedRanking(date: LocalDate, productId: Long, score: String) {
        redisTemplate.opsForZSet().add("ranking:all:${date.format(BASIC_ISO)}", productId.toString(), score.toDouble())
    }

    private companion object {
        private const val RANKINGS_PATH = "/api/v1/rankings"
        private const val PRODUCTS_PATH = "/api/v1/products"

        private const val PARAM_DATE = "date"
        private const val PARAM_PAGE = "page"
        private const val PARAM_SIZE = "size"

        private const val JSON_CODE = "$.code"
        private const val CODE_INVALID_PAGE_SIZE = "RANKING:INVALID_PAGE_SIZE"
        private const val CODE_INVALID_DATE_FORMAT = "RANKING:INVALID_DATE_FORMAT"
        private const val CODE_INVALID_PAGE = "RANKING:INVALID_PAGE"

        private val BASIC_ISO: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        private val TODAY: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
    }
}
