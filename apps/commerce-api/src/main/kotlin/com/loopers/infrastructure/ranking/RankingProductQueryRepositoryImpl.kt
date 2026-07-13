package com.loopers.infrastructure.ranking

import com.loopers.domain.product.dto.ProductSummary
import com.loopers.domain.ranking.RankingProductQueryRepository
import com.loopers.infrastructure.brand.entity.QBrandEntity.brandEntity
import com.loopers.infrastructure.product.entity.QProductEntity.productEntity
import com.loopers.infrastructure.product.entity.QProductStatEntity.productStatEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

@Component
class RankingProductQueryRepositoryImpl(
    entityManager: EntityManager,
) : RankingProductQueryRepository {
    private val queryFactory = JPAQueryFactory(entityManager)

    override fun findDisplayableSummaries(productIds: Collection<Long>): List<ProductSummary> {
        if (productIds.isEmpty()) {
            return emptyList()
        }

        return queryFactory
            .select(
                Projections.constructor(
                    ProductSummary::class.java,
                    productEntity.id,
                    productEntity.name,
                    productEntity.price,
                    productEntity.imageUrl,
                    brandEntity.id,
                    brandEntity.name,
                    productStatEntity.likeCount.coalesce(0L),
                ),
            )
            .from(productEntity)
            .join(brandEntity).on(brandEntity.id.eq(productEntity.brandId))
            .leftJoin(productStatEntity).on(productStatEntity.productId.eq(productEntity.id))
            .where(productEntity.id.`in`(productIds))
            .fetch()
    }
}
