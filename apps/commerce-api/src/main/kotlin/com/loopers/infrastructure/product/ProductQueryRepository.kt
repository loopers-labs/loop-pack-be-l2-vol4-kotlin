package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductSearchCondition
import com.loopers.domain.product.ProductSortType
import com.loopers.infrastructure.product.QProductJpaEntity.productJpaEntity
import com.loopers.projection.product.QProductLikeCountProjectionEntity.productLikeCountProjectionEntity
import com.loopers.support.paging.PageResult
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

@Component
class ProductQueryRepository(
    private val queryFactory: JPAQueryFactory,
) {
    fun findAll(condition: ProductSearchCondition): PageResult<Product> {
        val pageCondition = condition.pageCondition

        val items = when (condition.sortType) {
            ProductSortType.LIKES_DESC -> findAllByLikesDesc(condition)
            else -> findAllByProductSort(condition)
        }
        val totalElements = countProducts(condition)

        return PageResult(
            items = items,
            page = pageCondition.page,
            size = pageCondition.size,
            totalElements = totalElements,
            totalPages = pageCondition.totalPages(totalElements),
        )
    }

    private fun findAllByLikesDesc(condition: ProductSearchCondition): List<Product> {
        val pageCondition = condition.pageCondition

        return queryFactory
            .select(productJpaEntity)
            .from(productLikeCountProjectionEntity)
            .join(productJpaEntity).on(productJpaEntity.id.eq(productLikeCountProjectionEntity.productId))
            .where(
                productJpaEntity.deletedAt.isNull,
                likeCountBrandIdEq(condition.brandId),
            )
            .orderBy(productLikeCountProjectionEntity.likeCount.desc())
            .offset(pageCondition.offset())
            .limit(pageCondition.limit())
            .fetch()
            .map { it.toDomain() }
    }

    private fun findAllByProductSort(condition: ProductSearchCondition): List<Product> {
        val pageCondition = condition.pageCondition

        return queryFactory
            .selectFrom(productJpaEntity)
            .where(
                productJpaEntity.deletedAt.isNull,
                brandIdEq(condition.brandId),
            )
            .orderBy(condition.sortType.toOrderSpecifier())
            .offset(pageCondition.offset())
            .limit(pageCondition.limit())
            .fetch()
            .map { it.toDomain() }
    }

    private fun countProducts(condition: ProductSearchCondition): Long {
        return queryFactory
            .select(productJpaEntity.count())
            .from(productJpaEntity)
            .where(
                productJpaEntity.deletedAt.isNull,
                brandIdEq(condition.brandId),
            )
            .fetchOne() ?: 0L
    }

    private fun brandIdEq(brandId: Long?): BooleanExpression? {
        return brandId?.let { productJpaEntity.brandId.eq(it) }
    }

    private fun likeCountBrandIdEq(brandId: Long?): BooleanExpression? {
        return brandId?.let { productLikeCountProjectionEntity.brandId.eq(it) }
    }

    private fun ProductSortType.toOrderSpecifier(): OrderSpecifier<*> {
        return when (this) {
            ProductSortType.LATEST -> productJpaEntity.createdAt.desc()
            ProductSortType.PRICE_ASC -> productJpaEntity.price.asc()
            ProductSortType.LIKES_DESC -> productLikeCountProjectionEntity.likeCount.desc()
        }
    }
}
