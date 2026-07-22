package com.loopers.domain.ranking.application

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_도메인_생성
import com.loopers.domain.like.application.service.LikeService
import com.loopers.domain.product.application.service.ProductService
import com.loopers.domain.product.support.ProductSteps.Companion.상품_도메인_생성
import com.loopers.domain.ranking.application.command.RankingSearchCommand
import com.loopers.domain.ranking.port.ProductRankingReader
import com.loopers.domain.ranking.port.ProductRankingSnapshotReader
import com.loopers.domain.ranking.vo.RankingKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RankingFacadeTest {
    @Test
    fun `오늘_날짜_랭킹은_Redis에서_조회하고_순서를_유지한_rank를_부여한다`() {
        val fixture = fixture()
        every { fixture.rankingReader.findProductIds(오늘, 0, 20) } returns listOf(2L, 1L)
        every { fixture.rankingReader.count(오늘) } returns 2L
        every { fixture.productService.findByIds(listOf(2L, 1L)) } returns listOf(
            상품_도메인_생성(id = 1L),
            상품_도메인_생성(id = 2L),
        )
        every { fixture.brandService.getByIds(setOf(10L)) } returns listOf(브랜드_도메인_생성(id = 10L))
        every { fixture.likeService.countByProductIds(setOf(1L, 2L)) } returns mapOf(2L to 3L)

        val result = fixture.facade.findRankings(RankingSearchCommand(date = 오늘))

        assertThat(result.content.map { it.productId }).containsExactly(2L, 1L)
        assertThat(result.content.map { it.rank }).containsExactly(1L, 2L)
        assertThat(result.content.map { it.likeCount }).containsExactly(3L, 0L)
        assertThat(result.totalElements).isEqualTo(2L)
        verify(exactly = 0) { fixture.snapshotReader.findProductIds(any(), any(), any()) }
    }

    @Test
    fun `이틀_이전_날짜_랭킹은_RDB_스냅샷에서_조회한다`() {
        val fixture = fixture()
        val 과거_날짜 = 오늘.minusDays(2)
        every { fixture.snapshotReader.findProductIds(과거_날짜, 0, 20) } returns listOf(1L)
        every { fixture.snapshotReader.count(과거_날짜) } returns 1L
        every { fixture.productService.findByIds(listOf(1L)) } returns listOf(상품_도메인_생성(id = 1L))
        every { fixture.brandService.getByIds(setOf(10L)) } returns listOf(브랜드_도메인_생성(id = 10L))
        every { fixture.likeService.countByProductIds(setOf(1L)) } returns emptyMap()

        val result = fixture.facade.findRankings(RankingSearchCommand(date = 과거_날짜))

        assertThat(result.content.map { it.productId }).containsExactly(1L)
        verify(exactly = 0) { fixture.rankingReader.findProductIds(any(), any(), any()) }
    }

    @Test
    fun `페이지_오프셋을_반영해_rank를_계산한다`() {
        val fixture = fixture()
        every { fixture.rankingReader.findProductIds(오늘, 1, 2) } returns listOf(3L)
        every { fixture.rankingReader.count(오늘) } returns 3L
        every { fixture.productService.findByIds(listOf(3L)) } returns listOf(상품_도메인_생성(id = 3L))
        every { fixture.brandService.getByIds(setOf(10L)) } returns listOf(브랜드_도메인_생성(id = 10L))
        every { fixture.likeService.countByProductIds(setOf(3L)) } returns emptyMap()

        val result = fixture.facade.findRankings(RankingSearchCommand(date = 오늘, page = 1, size = 2))

        assertThat(result.content.single().rank).isEqualTo(3L)
        assertThat(result.hasNext).isFalse()
    }

    @Test
    fun `삭제된_상품은_제외하되_나머지_상품의_rank는_랭킹판_위치를_유지한다`() {
        val fixture = fixture()
        every { fixture.rankingReader.findProductIds(오늘, 0, 20) } returns listOf(2L, 9L, 1L)
        every { fixture.rankingReader.count(오늘) } returns 3L
        every { fixture.productService.findByIds(listOf(2L, 9L, 1L)) } returns listOf(
            상품_도메인_생성(id = 2L),
            상품_도메인_생성(id = 1L),
        )
        every { fixture.brandService.getByIds(setOf(10L)) } returns listOf(브랜드_도메인_생성(id = 10L))
        every { fixture.likeService.countByProductIds(setOf(2L, 1L)) } returns emptyMap()

        val result = fixture.facade.findRankings(RankingSearchCommand(date = 오늘))

        assertThat(result.content.map { it.productId }).containsExactly(2L, 1L)
        assertThat(result.content.map { it.rank }).containsExactly(1L, 3L)
    }

    @Test
    fun `빈_랭킹판이면_빈_목록과_totalCount_0을_반환한다`() {
        val fixture = fixture()
        every { fixture.rankingReader.findProductIds(오늘, 0, 20) } returns emptyList()
        every { fixture.rankingReader.count(오늘) } returns 0L

        val result = fixture.facade.findRankings(RankingSearchCommand(date = 오늘))

        assertThat(result.content).isEmpty()
        assertThat(result.totalElements).isZero()
    }

    private fun fixture(): Fixture {
        val rankingReader = mockk<ProductRankingReader>()
        val snapshotReader = mockk<ProductRankingSnapshotReader>()
        val productService = mockk<ProductService>()
        val brandService = mockk<BrandService>()
        val likeService = mockk<LikeService>()
        return Fixture(
            rankingReader = rankingReader,
            snapshotReader = snapshotReader,
            productService = productService,
            brandService = brandService,
            likeService = likeService,
            facade = RankingFacade(rankingReader, snapshotReader, productService, brandService, likeService),
        )
    }

    private data class Fixture(
        val rankingReader: ProductRankingReader,
        val snapshotReader: ProductRankingSnapshotReader,
        val productService: ProductService,
        val brandService: BrandService,
        val likeService: LikeService,
        val facade: RankingFacade,
    )

    companion object {
        private val 오늘: LocalDate = LocalDate.now(RankingKey.ZONE)
    }
}
