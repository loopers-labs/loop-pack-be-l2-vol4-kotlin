package com.loopers.application.ranking

import com.loopers.application.ranking.usecase.GetRankingsUsecase
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.support.error.CoreException
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.RedisTemplate
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@SpringBootTest
class GetRankingsUsecaseIntegrationTest @Autowired constructor(
    private val usecase: GetRankingsUsecase,
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

    private fun seedProduct(name: String, price: String): ProductModel {
        val brand = brandRepository.save(BrandModel(name = "brand-$name", description = "d"))
        return productRepository.save(
            ProductModel(brandId = brand.id, name = name, description = "d", price = BigDecimal(price)),
        )
    }

    @DisplayName("랭킹 페이지는 score 내림차순으로 상품정보가 aggregation되어 반환된다 — 1-based rank.")
    @Test
    fun returnsAggregatedRankingPage() {
        val first = seedProduct("first", "10000.00")
        val second = seedProduct("second", "20000.00")
        val key = "ranking:all:v1:$today"
        redisTemplate.opsForZSet().add(key, "${first.id}", 9.0)
        redisTemplate.opsForZSet().add(key, "${second.id}", 4.0)

        // date 명시 — 시딩 키와 조회 키를 고정 일치시켜 자정 경계 플래키 제거(PR #135 review)
        val result = usecase.execute(GetRankingsUsecase.Query(period = RankingPeriod.DAILY, date = today, page = 1, size = 20))

        assertThat(result.totalCount).isEqualTo(2L)
        assertThat(result.date).isEqualTo(today)
        assertThat(result.items).hasSize(2)
        assertThat(result.items[0].rank).isEqualTo(1L)
        assertThat(result.items[0].productId).isEqualTo(first.id)
        assertThat(result.items[0].name).isEqualTo("first")
        assertThat(result.items[0].brandName).isEqualTo("brand-first")
        assertThat(result.items[0].score).isEqualTo(9.0)
        assertThat(result.items[1].rank).isEqualTo(2L)
    }

    @DisplayName("2페이지는 offset 이후 구간을 rank 연속으로 반환한다.")
    @Test
    fun paginatesWithContinuousRank() {
        val key = "ranking:all:v1:$today"
        val products = (1..3).map { seedProduct("p$it", "1000.00") }
        products.forEachIndexed { index, p -> redisTemplate.opsForZSet().add(key, "${p.id}", 10.0 - index) }

        val result = usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, today, page = 2, size = 2))

        assertThat(result.items).hasSize(1)
        assertThat(result.items[0].rank).isEqualTo(3L)
        assertThat(result.items[0].productId).isEqualTo(products[2].id)
    }

    @DisplayName("삭제(soft delete)된 상품은 aggregation에서 제외되고, 빈 키는 빈 목록을 반환한다.")
    @Test
    fun skipsDeletedProductAndHandlesEmptyKey() {
        val alive = seedProduct("alive", "1000.00")
        val deleted = seedProduct("deleted", "1000.00")
        deleted.softDelete()
        productRepository.save(deleted)
        val key = "ranking:all:v1:$today"
        redisTemplate.opsForZSet().add(key, "${alive.id}", 2.0)
        redisTemplate.opsForZSet().add(key, "${deleted.id}", 9.0)

        val result = usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, today, page = 1, size = 20))
        assertThat(result.items.map { it.productId }).containsExactly(alive.id)
        // 스킵된 상품의 자리는 재넘버링하지 않는다 — rank는 ZSET상 실제 순위(스펙 §6)
        assertThat(result.items[0].rank).isEqualTo(2L)

        val empty = usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, "19990101", page = 1, size = 20))
        assertThat(empty.items).isEmpty()
        assertThat(empty.totalCount).isZero()
    }

    @DisplayName("삭제(soft delete)된 브랜드의 상품은 aggregation에서 제외된다.")
    @Test
    fun skipsProductOfDeletedBrand() {
        val aliveBrand = brandRepository.save(BrandModel(name = "brand-alive", description = "d"))
        val deadBrand = brandRepository.save(BrandModel(name = "brand-dead", description = "d"))
        val alive = productRepository.save(
            ProductModel(brandId = aliveBrand.id, name = "alive", description = "d", price = BigDecimal("1000.00")),
        )
        val orphan = productRepository.save(
            ProductModel(brandId = deadBrand.id, name = "orphan", description = "d", price = BigDecimal("1000.00")),
        )
        deadBrand.softDelete()
        brandRepository.save(deadBrand)
        val key = "ranking:all:v1:$today"
        redisTemplate.opsForZSet().add(key, "${alive.id}", 2.0)
        redisTemplate.opsForZSet().add(key, "${orphan.id}", 9.0)

        val result = usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, today, page = 1, size = 20))

        assertThat(result.items.map { it.productId }).containsExactly(alive.id)
    }

    @DisplayName("page < 1, size 범위 밖은 BAD_REQUEST를 던진다.")
    @Test
    fun validatesPaging() {
        assertThatThrownBy { usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, null, page = 0, size = 20)) }
            .isInstanceOf(CoreException::class.java)
        assertThatThrownBy { usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, null, page = 1, size = 0)) }
            .isInstanceOf(CoreException::class.java)
        assertThatThrownBy { usecase.execute(GetRankingsUsecase.Query(RankingPeriod.DAILY, null, page = 1, size = 101)) }
            .isInstanceOf(CoreException::class.java)
    }
}
