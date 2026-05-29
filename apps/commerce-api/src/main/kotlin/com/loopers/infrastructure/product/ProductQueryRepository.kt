package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductSearchCondition
import com.loopers.domain.product.ProductSortType
import com.loopers.infrastructure.product.QProductJpaEntity.productJpaEntity
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

        val items = queryFactory
            .selectFrom(productJpaEntity)
            .where(
                productJpaEntity.deletedAt.isNull,
                brandIdEq(condition.brandId),
            )
            .orderBy(condition.sortType.toOrderSpecifier())
            .offset((pageCondition.page * pageCondition.size).toLong())
            .limit(pageCondition.size.toLong())
            .fetch()
            .map { it.toDomain() }

        val totalElements = queryFactory
            .select(productJpaEntity.count())
            .from(productJpaEntity)
            .where(
                productJpaEntity.deletedAt.isNull,
                brandIdEq(condition.brandId),
            )
            .fetchOne() ?: 0L

        return PageResult(
            items = items,
            page = pageCondition.page,
            size = pageCondition.size,
            totalElements = totalElements,
            totalPages = totalElements.toTotalPages(pageCondition.size),
        )
    }

    private fun brandIdEq(brandId: Long?): BooleanExpression? {
        return brandId?.let { productJpaEntity.brandId.eq(it) }
    }

    private fun ProductSortType.toOrderSpecifier(): OrderSpecifier<*> {
        return when (this) {
            ProductSortType.LATEST -> productJpaEntity.createdAt.desc()
            ProductSortType.PRICE_ASC -> productJpaEntity.price.asc()
            ProductSortType.LIKES_DESC -> productJpaEntity.likeCount.desc()
        }
    }

    private fun Long.toTotalPages(size: Int): Int {
        if (this == 0L) return 0
        return ((this + size - 1) / size).toInt()
    }
}
