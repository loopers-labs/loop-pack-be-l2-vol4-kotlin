package com.loopers.domain.like.integration

import com.loopers.domain.brand.application.service.BrandService
import com.loopers.domain.brand.support.BrandSteps.Companion.브랜드_등록_커맨드
import com.loopers.domain.like.application.service.LikeCountReconciliationService
import com.loopers.domain.like.infrastructure.persistence.LikeJpaEntity
import com.loopers.domain.like.infrastructure.persistence.LikeJpaId
import com.loopers.domain.like.infrastructure.persistence.LikeJpaRepository
import com.loopers.domain.like.infrastructure.persistence.ProductLikeCountJpaRepository
import com.loopers.domain.like.port.LikeBulkRepository
import com.loopers.domain.product.infrastructure.persistence.product.ProductJpaRepository
import com.loopers.domain.product.model.ProductModel
import com.loopers.domain.product.port.ProductBulkRepository
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.ProductName
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(
    properties = ["commerce-events.outbox-relay.enabled=false"],
)
class LikeCountReconciliationIntegrationTest
    @Autowired
    constructor(
        private val likeCountReconciliationService: LikeCountReconciliationService,
        private val brandService: BrandService,
        private val productBulkRepository: ProductBulkRepository,
        private val productJpaRepository: ProductJpaRepository,
        private val likeJpaRepository: LikeJpaRepository,
        private val productLikeCountJpaRepository: ProductLikeCountJpaRepository,
        private val likeBulkRepository: LikeBulkRepository,
        private val jdbcTemplate: JdbcTemplate,
        private val databaseCleanUp: DatabaseCleanUp,
    ) {
        @AfterEach
        fun tearDown() {
            databaseCleanUp.truncateAllTables()
        }

        @Test
        fun `상품만_벌크_적재된_뒤_좋아요_수_projection을_likes_기준으로_재구성한다`() {
            val productIds = seedProductsWithoutLikeCountRows()
            likeJpaRepository.saveAllAndFlush(
                listOf(
                    LikeJpaEntity(LikeJpaId(userId = 1L, productId = productIds[0])),
                    LikeJpaEntity(LikeJpaId(userId = 2L, productId = productIds[0])),
                    LikeJpaEntity(LikeJpaId(userId = 1L, productId = productIds[1])),
                ),
            )
            assertThat(productLikeCountJpaRepository.count()).isZero()

            val firstResult = likeCountReconciliationService.rebuildFromLikes()
            val firstCounts = findCounts(productIds)
            val secondResult = likeCountReconciliationService.rebuildFromLikes()
            val secondCounts = findCounts(productIds)

            assertThat(firstResult.projectionRows).isEqualTo(3L)
            assertThat(secondResult.projectionRows).isEqualTo(3L)
            assertThat(secondCounts).isEqualTo(firstCounts)
            assertThat(secondCounts).containsEntry(productIds[0], 2L)
            assertThat(secondCounts).containsEntry(productIds[1], 1L)
            assertThat(secondCounts).containsEntry(productIds[2], 0L)
        }

        @Test
        fun `벌크_좋아요_집계는_product_metrics_updated_at을_채운다`() {
            seedProductsWithoutLikeCountRows()

            val inserted = likeBulkRepository.deriveLikeCounts()

            assertThat(inserted).isEqualTo(3)
            assertThat(productLikeCountJpaRepository.count()).isEqualTo(3)
            assertThat(productMetricsNullUpdatedAtRows()).isZero()
        }

        private fun findCounts(productIds: List<Long>): Map<Long, Long> =
            productLikeCountJpaRepository.findCountsByProductIds(productIds.toSet())
                .associate { it.getProductId() to it.getLikeCount() }

        private fun seedProductsWithoutLikeCountRows(): List<Long> {
            val brand = brandService.register(브랜드_등록_커맨드())
            val products = (1..3).map { seq ->
                ProductModel(
                    brandId = brand.id,
                    name = ProductName.of("reconcile-product-$seq"),
                    price = Money.of(1_000L + seq),
                )
            }
            productBulkRepository.bulkInsert(products)
            return productJpaRepository.findAll()
                .map { it.id }
                .sorted()
        }

        private fun productMetricsNullUpdatedAtRows(): Long =
            jdbcTemplate.queryForObject(
                "select count(*) from product_metrics where updated_at is null",
                Long::class.java,
            ) ?: 0L
    }
