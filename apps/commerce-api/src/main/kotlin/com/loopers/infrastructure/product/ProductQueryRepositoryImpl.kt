package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.dto.ProductSummary
import com.loopers.infrastructure.brand.QBrand.brand
import com.loopers.infrastructure.product.QProduct.product
import com.loopers.infrastructure.productstat.QProductStat.productStat
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
        val content = queryFactory
            .select(
                Projections.constructor(
                    ProductSummary::class.java,
                    product.id,
                    product.name,
                    product.price,
                    product.imageUrl,
                    brand.id,
                    brand.name,
                    productStat.likeCount.coalesce(0L),
                ),
            )
            .from(product)
            .join(brand).on(brand.id.eq(product.brandId))
            .leftJoin(productStat).on(productStat.productId.eq(product.id))
            .where(
                product.isDeleted.isFalse,
                brand.isDeleted.isFalse,
                brandIdEq(brandId),
            )
            .orderBy(*orderSpecifiers(sort))
            .offset(pageable.offset)
            .limit(pageable.pageSize.toLong())
            .fetch()

        return PageableExecutionUtils.getPage(content, pageable) {
            queryFactory
                .select(product.count())
                .from(product)
                .join(brand).on(brand.id.eq(product.brandId))
                .where(
                    product.isDeleted.isFalse,
                    brand.isDeleted.isFalse,
                    brandIdEq(brandId),
                )
                .fetchOne() ?: 0L
        }
    }

    private fun brandIdEq(brandId: Long?): BooleanExpression? {
        return brandId?.let { product.brandId.eq(it) }
    }

    private fun orderSpecifiers(sort: ProductSort): Array<OrderSpecifier<*>> {
        return when (sort) {
            ProductSort.LATEST -> arrayOf(
                product.createdAt.desc(),
                product.id.desc(),
            )
            ProductSort.PRICE_ASC -> arrayOf(
                product.price.asc(),
                product.id.desc(),
            )
            ProductSort.LIKES_DESC -> arrayOf(
                productStat.likeCount.coalesce(0L).desc(),
                product.createdAt.desc(),
                product.id.desc(),
            )
        }
    }
}
