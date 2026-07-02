package com.loopers.domain.product.infrastructure.persistence.product

import com.loopers.domain.brand.infrastructure.persistence.QBrandJpaEntity
import com.loopers.domain.like.infrastructure.persistence.QProductLikeCountJpaEntity
import com.loopers.domain.product.constant.ProductErrorMessages
import com.loopers.domain.product.model.ProductModel
import com.loopers.domain.product.port.ProductRepository
import com.loopers.domain.product.port.ProductSearchCondition
import com.loopers.domain.product.vo.ProductSort
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : ProductRepository {
    override fun save(product: ProductModel): ProductModel {
        val entity = if (product.id == 0L) {
            ProductJpaEntity.fromDomain(product)
        } else {
            productJpaRepository.findById(product.id).orElseThrow()
                .also { it.updateFrom(product) }
        }
        return productJpaRepository.saveAndFlush(entity).toDomain()
    }

    override fun saveAll(products: List<ProductModel>): List<ProductModel> = products.map { save(it) }

    override fun findByIdOrNull(productId: Long): ProductModel? =
        productJpaRepository.findById(productId).map { it.toDomain() }.orElse(null)

    override fun findAllByIds(productIds: Collection<Long>): List<ProductModel> =
        productJpaRepository.findAllByIds(productIds).map { it.toDomain() }

    override fun findByBrandId(brandId: Long): List<ProductModel> =
        productJpaRepository.findByBrandId(brandId).map { it.toDomain() }

    override fun findByCondition(condition: ProductSearchCondition): List<ProductModel> {
        if (condition.sort == ProductSort.LIKES_DESC) {
            return findByLikesDesc(condition)
        }
        val pageable = PageRequest.of(
            condition.page,
            condition.size,
            condition.sort.toJpaSort(),
        )
        return productJpaRepository.findActiveProducts(
            brandId = condition.brandId,
            pageable = pageable,
        ).map { it.toDomain() }
    }

    private fun findByLikesDesc(condition: ProductSearchCondition): List<ProductModel> {
        val product = QProductJpaEntity.productJpaEntity
        val brand = QBrandJpaEntity.brandJpaEntity
        val likeCount = QProductLikeCountJpaEntity.productLikeCountJpaEntity

        return queryFactory
            .select(product)
            .from(product)
            .join(brand).on(brand.id.eq(product.brandId))
            .leftJoin(likeCount).on(likeCount.productId.eq(product.id))
            .where(
                product.deletedAt.isNull,
                brand.deletedAt.isNull,
                brandIdEq(condition.brandId),
            )
            .orderBy(
                likeCount.likeCount.coalesce(0L).desc(),
                product.id.desc(),
            )
            .offset(condition.page.toLong() * condition.size)
            .limit(condition.size.toLong())
            .fetch()
            .map { it.toDomain() }
    }

    private fun brandIdEq(brandId: Long?): BooleanExpression? {
        val product = QProductJpaEntity.productJpaEntity
        return brandId?.let { product.brandId.eq(it) }
    }

    private fun ProductSort.toJpaSort(): Sort =
        when (this) {
            ProductSort.LATEST -> Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"))
            ProductSort.PRICE_ASC -> Sort.by(Sort.Direction.ASC, "price")
                .and(Sort.by(Sort.Direction.DESC, "id"))
            ProductSort.LIKES_DESC -> throw IllegalArgumentException(ProductErrorMessages.LIKES_SORT_HANDLED_BY_QUERYDSL)
        }
}
