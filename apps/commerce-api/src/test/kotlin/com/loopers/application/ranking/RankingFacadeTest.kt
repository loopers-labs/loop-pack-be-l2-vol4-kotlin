package com.loopers.application.ranking

import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandService
import com.loopers.domain.brand.InMemoryBrandRepository
import com.loopers.domain.like.InMemoryLikeRepository
import com.loopers.domain.like.InMemoryProductLikeCountRepository
import com.loopers.domain.like.LikeService
import com.loopers.domain.product.InMemoryProductRepository
import com.loopers.domain.product.Level
import com.loopers.domain.product.ProductModel
import com.loopers.domain.product.ProductService
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.product.TechCategory
import com.loopers.domain.ranking.InMemoryRankingRepository
import com.loopers.domain.ranking.RankingEntry
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class RankingFacadeTest {
    private val rankingRepository = InMemoryRankingRepository()
    private val productRepository = InMemoryProductRepository()
    private val brandRepository = InMemoryBrandRepository()
    private val likeCountRepository = InMemoryProductLikeCountRepository()

    private val rankingService = RankingService(rankingRepository)
    private val productService = ProductService(productRepository)
    private val brandService = BrandService(brandRepository)
    private val likeService = LikeService(InMemoryLikeRepository(), likeCountRepository)

    private val clock = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneId.of("Asia/Seoul"))

    private val rankingFacade = RankingFacade(rankingService, productService, brandService, likeService, clock)

    private val pageable = PageRequest.of(0, 20)

    private fun saveBrand(name: String): BrandModel = brandRepository.save(BrandModel.of(name))

    private fun saveProduct(
        name: String,
        brandId: Long,
        price: Double = 1000.0,
        status: ProductStatus = ProductStatus.ACTIVE,
    ): ProductModel {
        val product = ProductModel.of(
            brandId = brandId,
            isbn = "isbn-$name",
            name = name,
            authName = "저자",
            techCategory = TechCategory.BACKEND,
            level = Level.BEGINNER,
            price = price,
            description = "설명",
        )
        if (status != ProductStatus.ACTIVE) product.changeStatus(status)
        return productRepository.save(product)
    }

    private fun setRanking(vararg entries: RankingEntry) {
        rankingRepository.entries.clear()
        rankingRepository.entries.addAll(entries)
    }

    private fun setLikeCount(productId: Long, count: Int) {
        likeCountRepository.upsertLikeCount(productId, count, ZonedDateTime.now())
    }

    @DisplayName("순위 순서대로 상품 요약을 합성해 반환한다 (상품 저장 순서가 아니라 랭킹 순서)")
    @Test
    fun preservesRankOrder() {
        val brand = saveBrand("브랜드A")
        val p1 = saveProduct("상품1", brand.id, price = 1000.0)
        val p2 = saveProduct("상품2", brand.id, price = 2000.0)
        setLikeCount(p1.id, 5)
        setLikeCount(p2.id, 3)
        setRanking(RankingEntry(p2.id, 1L), RankingEntry(p1.id, 2L))

        val page = rankingFacade.getRankings(RankingPeriod.DAILY, null, pageable)

        assertThat(page.content).hasSize(2)
        assertThat(page.content.map { it.productId }).containsExactly(p2.id, p1.id)
        assertThat(page.content[0].rank).isEqualTo(1L)
        assertThat(page.content[0].name).isEqualTo("상품2")
        assertThat(page.content[0].brandName).isEqualTo("브랜드A")
        assertThat(page.content[0].price).isEqualTo(2000)
        assertThat(page.content[0].likeCount).isEqualTo(3)
        assertThat(page.content[1].productId).isEqualTo(p1.id)
        assertThat(page.content[1].rank).isEqualTo(2L)
    }

    @DisplayName("삭제/비활성이거나 존재하지 않는 상품은 결과에서 제외한다 (total 은 랭킹 기준 유지)")
    @Test
    fun skipsMissingOrInactive() {
        val brand = saveBrand("브랜드A")
        val active = saveProduct("정상", brand.id)
        val deleted = saveProduct("삭제됨", brand.id, status = ProductStatus.DELETED)
        setRanking(
            RankingEntry(active.id, 1L),
            RankingEntry(deleted.id, 2L),
            RankingEntry(999L, 3L),
        )

        val page = rankingFacade.getRankings(RankingPeriod.DAILY, null, pageable)

        assertThat(page.content).hasSize(1)
        assertThat(page.content.map { it.productId }).containsExactly(active.id)
    }

    @DisplayName("랭킹이 비어있으면 빈 페이지를 반환한다")
    @Test
    fun emptyRanking() {
        setRanking()

        val page = rankingFacade.getRankings(RankingPeriod.DAILY, null, pageable)

        assertThat(page.content).isEmpty()
        assertThat(page.totalElements).isZero()
    }

    @DisplayName("좋아요 집계가 없으면 0, 브랜드가 없으면 unknown 으로 채운다")
    @Test
    fun defaultsForMissingLikeAndBrand() {
        val product = saveProduct("상품", brandId = 999L) // 브랜드 999 는 저장 안 함
        setRanking(RankingEntry(product.id, 1L))

        val page = rankingFacade.getRankings(RankingPeriod.DAILY, null, pageable)

        assertThat(page.content).hasSize(1)
        assertThat(page.content[0].likeCount).isEqualTo(0)
        assertThat(page.content[0].brandName).isEqualTo("unknown")
    }
}
