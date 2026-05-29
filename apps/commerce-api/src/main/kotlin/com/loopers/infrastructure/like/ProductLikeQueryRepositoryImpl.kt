package com.loopers.infrastructure.like

import com.loopers.domain.product.dto.ProductSummary
import com.loopers.infrastructure.brand.QBrandEntity.brandEntity
import com.loopers.infrastructure.like.QProductLikeEntity.productLikeEntity
import com.loopers.infrastructure.product.QProductEntity.productEntity
import com.loopers.infrastructure.productstat.QProductStatEntity.productStatEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils

class ProductLikeQueryRepositoryImpl(
    entityManager: EntityManager,
) : ProductLikeQueryRepository {
    private val queryFactory = JPAQueryFactory(entityManager)

    override fun findLikedProductSummaries(memberId: Long, pageable: Pageable): Page<ProductSummary> {
        val content = queryFactory
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
            .from(productLikeEntity)
            .join(productEntity).on(productEntity.id.eq(productLikeEntity.productId))
            .join(brandEntity).on(brandEntity.id.eq(productEntity.brandId))
            .leftJoin(productStatEntity).on(productStatEntity.productId.eq(productEntity.id))
            .where(
                productLikeEntity.memberId.eq(memberId),
                productEntity.isDeleted.isFalse,
                brandEntity.isDeleted.isFalse,
            )
            .orderBy(productLikeEntity.createdAt.desc(), productLikeEntity.id.desc())
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        return PageableExecutionUtils.getPage(content, pageable) {
            queryFactory
                .select(productLikeEntity.count())
                .from(productLikeEntity)
                .join(productEntity).on(productEntity.id.eq(productLikeEntity.productId))
                .join(brandEntity).on(brandEntity.id.eq(productEntity.brandId))
                .where(
                    productLikeEntity.memberId.eq(memberId),
                    productEntity.isDeleted.isFalse,
                    brandEntity.isDeleted.isFalse,
                )
                .fetchOne() ?: 0L
        }
    }
}
