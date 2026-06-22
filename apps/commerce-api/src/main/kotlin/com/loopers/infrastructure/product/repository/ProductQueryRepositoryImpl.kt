package com.loopers.infrastructure.product.repository

import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.infrastructure.brand.entity.QBrandEntity.brandEntity
import com.loopers.infrastructure.product.entity.QProductEntity.productEntity
import com.loopers.infrastructure.product.entity.QProductStatEntity.productStatEntity
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.support.PageableExecutionUtils

class ProductQueryRepositoryImpl(
    entityManager: EntityManager,
) : ProductQueryRepository {
    private val queryFactory = JPAQueryFactory(entityManager)

    override fun findDisplayableSummaries(
        brandId: Long?,
        sort: ProductSort,
        pageable: Pageable,
    ): Page<ProductSummary> {
        if (sort == ProductSort.LIKES_DESC) {
            return findDisplayableSummariesByLikes(brandId = brandId, pageable = pageable)
        }

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
            .from(productEntity)
            .join(brandEntity).on(brandEntity.id.eq(productEntity.brandId))
            .leftJoin(productStatEntity).on(productStatEntity.productId.eq(productEntity.id))
            .where(
                brandIdEq(brandId),
            )
            .orderBy(*orderSpecifiers(sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        return PageableExecutionUtils.getPage(content, pageable) {
            queryFactory
                .select(productEntity.count())
                .from(productEntity)
                .join(brandEntity).on(brandEntity.id.eq(productEntity.brandId))
                .where(
                    brandIdEq(brandId),
                )
                .fetchOne() ?: 0L
        }
    }

    private fun findDisplayableSummariesByLikes(
        brandId: Long?,
        pageable: Pageable,
    ): Page<ProductSummary> {
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
                    productStatEntity.likeCount,
                ),
            )
            .from(productStatEntity)
            .join(productEntity).on(productEntity.id.eq(productStatEntity.productId))
            .join(brandEntity).on(brandEntity.id.eq(productEntity.brandId))
            .where(
                productStatBrandIdEq(brandId),
            )
            .orderBy(
                productStatEntity.likeCount.desc(),
                productStatEntity.productId.desc(),
            )
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        return PageableExecutionUtils.getPage(content, pageable) {
            queryFactory
                .select(productStatEntity.productId.count())
                .from(productStatEntity)
                .join(productEntity).on(productEntity.id.eq(productStatEntity.productId))
                .join(brandEntity).on(brandEntity.id.eq(productEntity.brandId))
                .where(
                    productStatBrandIdEq(brandId),
                )
                .fetchOne() ?: 0L
        }
    }

    private fun brandIdEq(brandId: Long?): BooleanExpression? {
        return brandId?.let { productEntity.brandId.eq(it) }
    }

    private fun productStatBrandIdEq(brandId: Long?): BooleanExpression? {
        return brandId?.let { productStatEntity.brandId.eq(it) }
    }

    private fun orderSpecifiers(sort: ProductSort): Array<OrderSpecifier<*>> {
        return when (sort) {
            ProductSort.LATEST -> arrayOf(
                productEntity.createdAt.desc(),
                productEntity.id.desc(),
            )
            ProductSort.PRICE_ASC -> arrayOf(
                productEntity.price.asc(),
                productEntity.id.desc(),
            )
            ProductSort.LIKES_DESC -> arrayOf(
                productStatEntity.likeCount.desc(),
                productStatEntity.productId.desc(),
            )
        }
    }
}
