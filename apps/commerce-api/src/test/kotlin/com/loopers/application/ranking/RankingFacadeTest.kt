package com.loopers.application.ranking

import com.loopers.application.ranking.result.RankedProductResult
import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.RankedEntry
import com.loopers.domain.ranking.RankingRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RankingFacadeTest {
    private val rankingRepository = mockk<RankingRepository>()
    private val productRepository = mockk<ProductRepository>()
    private val brandRepository = mockk<BrandRepository>()
    private val facade = RankingFacade(rankingRepository, productRepository, brandRepository)

    @DisplayName("랭킹 조회는,")
    @Nested
    inner class GetRanking {
        @Test
        fun `점수 내림차순으로 상품 정보가 조립된 목록을 반환한다`() {
            every { rankingRepository.topN("rank:all:20260714", 0, 20) } returns listOf(
                RankedEntry(productId = 202L, score = 3.0),
                RankedEntry(productId = 303L, score = 2.0),
            )
            every { productRepository.findAllByIds(listOf(202L, 303L)) } returns listOf(
                ProductFixture.validProduct(id = 202L, name = "B", price = 2000, likeCount = 5, brandId = 9L),
                ProductFixture.validProduct(id = 303L, name = "C", price = 3000, likeCount = 1, brandId = 9L),
            )
            every { brandRepository.findAllByIds(listOf(9L)) } returns listOf(
                BrandFixture.validBrand(id = 9L, name = "나이키"),
            )

            val result = facade.getRanking(date = "20260714", page = 0, size = 20)

            assertThat(result).containsExactly(
                RankedProductResult(productId = 202L, name = "B", price = 2000, brandName = "나이키", likeCount = 5, rank = 1, score = 3.0),
                RankedProductResult(productId = 303L, name = "C", price = 3000, brandName = "나이키", likeCount = 1, rank = 2, score = 2.0),
            )
        }
    }
}
