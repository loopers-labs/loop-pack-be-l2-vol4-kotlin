package com.loopers.infrastructure.product

import com.loopers.domain.product.LikeCountCursor
import com.loopers.domain.product.PriceCursor
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.product.ProductSort
import com.loopers.domain.product.ProductStatus
import com.loopers.domain.product.ProductErrorCode
import com.loopers.domain.shared.Cursor
import com.loopers.domain.shared.CursorPage
import com.loopers.domain.shared.IdCursor
import com.loopers.support.error.BadRequestException
import org.springframework.data.domain.Limit
import org.springframework.data.domain.ScrollPosition
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class ProductRepositoryImpl(
    private val productJpaRepository: ProductJpaRepository,
) : ProductRepository {
    override fun save(product: Product): Product =
        productJpaRepository.save(product)

    override fun findActiveById(id: Long): Product? =
        productJpaRepository.findByIdAndStatusNot(id, ProductStatus.DELETED)

    override fun findAllActiveByIdIn(ids: List<Long>): List<Product> =
        productJpaRepository.findByIdInAndStatusNot(ids, ProductStatus.DELETED)

    override fun findActiveByBrandId(brandId: Long): List<Product> =
        productJpaRepository.findByBrandIdAndStatusNot(brandId, ProductStatus.DELETED)

    override fun findAll(sort: ProductSort, brandId: Long?, cursor: Cursor?, size: Int): CursorPage<Product> {
        val scrollPosition = scrollPosition(sort, cursor)
        val window = if (brandId == null) {
            productJpaRepository.findByStatusNot(ProductStatus.DELETED, scrollPosition, Limit.of(size), sort.toSort())
        } else {
            productJpaRepository.findByBrandIdAndStatusNot(
                brandId,
                ProductStatus.DELETED,
                scrollPosition,
                Limit.of(size),
                sort.toSort(),
            )
        }
        val nextCursor =
            if (window.hasNext() && window.content.isNotEmpty()) {
                sort.nextCursor(window.content.last())
            } else {
                null
            }
        return CursorPage(window.content, window.hasNext(), nextCursor)
    }

    private fun scrollPosition(sort: ProductSort, cursor: Cursor?): ScrollPosition {
        if (cursor == null) {
            return ScrollPosition.keyset()
        }
        // 정렬과 커서 타입은 짝이 맞아야 한다. 어긋나면 클라이언트가 잘못 준 입력이므로 400.
        val keys: Map<String, Any> = when (sort) {
            ProductSort.LATEST -> {
                val idCursor = cursor as? IdCursor ?: throw invalidCursor()
                mapOf("id" to idCursor.id)
            }
            ProductSort.PRICE_ASC -> {
                val priceCursor = cursor as? PriceCursor ?: throw invalidCursor()
                mapOf(PRICE_PROPERTY to priceCursor.price, "id" to priceCursor.id)
            }
            ProductSort.LIKES_DESC -> {
                val likeCountCursor = cursor as? LikeCountCursor ?: throw invalidCursor()
                mapOf(LIKE_COUNT_PROPERTY to likeCountCursor.likeCount, "id" to likeCountCursor.id)
            }
        }
        return ScrollPosition.of(keys, ScrollPosition.Direction.FORWARD)
    }

    private fun invalidCursor(): BadRequestException =
        BadRequestException(ProductErrorCode.INVALID_PRODUCT_CURSOR)

    private fun ProductSort.toSort(): Sort =
        when (this) {
            ProductSort.LATEST -> Sort.by(Sort.Direction.DESC, "id")
            ProductSort.PRICE_ASC -> Sort.by(Sort.Order.asc(PRICE_PROPERTY), Sort.Order.desc("id"))
            ProductSort.LIKES_DESC -> Sort.by(Sort.Order.desc(LIKE_COUNT_PROPERTY), Sort.Order.desc("id"))
        }

    private fun ProductSort.nextCursor(last: Product): Cursor =
        when (this) {
            ProductSort.LATEST -> IdCursor(last.id)
            ProductSort.PRICE_ASC -> PriceCursor(last.price.amount, last.id)
            ProductSort.LIKES_DESC -> LikeCountCursor(last.likeCount, last.id)
        }

    companion object {
        private const val PRICE_PROPERTY = "price.amount"
        private const val LIKE_COUNT_PROPERTY = "likeCount"
    }
}
