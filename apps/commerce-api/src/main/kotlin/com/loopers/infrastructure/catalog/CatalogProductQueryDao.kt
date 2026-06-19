package com.loopers.infrastructure.catalog

import com.loopers.application.catalog.CatalogInfo
import com.loopers.application.catalog.ProductSort
import com.loopers.application.catalog.port.CatalogProductQueryPort
import com.loopers.domain.catalog.BrandStatus
import com.loopers.domain.catalog.ProductStatus
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component

@Component
class CatalogProductQueryDao(
    private val entityManager: EntityManager,
) : CatalogProductQueryPort {
    override fun findDisplayableProducts(sort: ProductSort, page: Int, size: Int): List<CatalogInfo.ProductDisplayRow> {
        validatePage(page, size)
        val query = entityManager.createQuery(
            """
            select p.id, p.name, b.id, b.name, p.price, stats.likeCount, stock.stockQuantity, stock.reservedQuantity
              from Product p, Brand b, ProductStats stats, ProductStock stock
             where p.deletedAt is null
               and p.status = :productStatus
               and b.deletedAt is null
               and b.status = :brandStatus
               and p.brandId = b.id
               and stats.deletedAt is null
               and stats.productId = p.id
               and stock.deletedAt is null
               and stock.productId = p.id
             ${orderBy(sort)}
            """.trimIndent(),
        )
        query.setParameter("productStatus", ProductStatus.ON_SALE)
        query.setParameter("brandStatus", BrandStatus.ACTIVE)
        query.firstResult = page * size
        query.maxResults = size
        return query.resultList.map { row ->
            val values = row as Array<*>
            CatalogInfo.ProductDisplayRow(
                productId = values[0] as Long,
                productName = values[1] as String,
                brandId = values[2] as Long,
                brandName = values[3] as String,
                price = values[4] as Long,
                likeCount = values[5] as Long,
                stockQuantity = values[6] as Int,
                reservedQuantity = values[7] as Int,
            )
        }
    }

    override fun findDisplayableProductsByBrandId(
        brandId: Long,
        sort: ProductSort,
        page: Int,
        size: Int,
    ): List<CatalogInfo.ProductDisplayRow> {
        validatePage(page, size)
        val query = entityManager.createQuery(
            """
            select p.id, p.name, b.id, b.name, p.price, stats.likeCount, stock.stockQuantity, stock.reservedQuantity
              from Product p, Brand b, ProductStats stats, ProductStock stock
             where b.id = :brandId
               and p.deletedAt is null
               and p.status = :productStatus
               and b.deletedAt is null
               and b.status = :brandStatus
               and p.brandId = b.id
               and stats.deletedAt is null
               and stats.productId = p.id
               and stock.deletedAt is null
               and stock.productId = p.id
             ${orderBy(sort)}
            """.trimIndent(),
        )
        query.setParameter("brandId", brandId)
        query.setParameter("productStatus", ProductStatus.ON_SALE)
        query.setParameter("brandStatus", BrandStatus.ACTIVE)
        query.firstResult = page * size
        query.maxResults = size
        return query.resultList.map { row ->
            val values = row as Array<*>
            CatalogInfo.ProductDisplayRow(
                productId = values[0] as Long,
                productName = values[1] as String,
                brandId = values[2] as Long,
                brandName = values[3] as String,
                price = values[4] as Long,
                likeCount = values[5] as Long,
                stockQuantity = values[6] as Int,
                reservedQuantity = values[7] as Int,
            )
        }
    }

    override fun findDisplayableProductDetail(productId: Long): CatalogInfo.ProductDetailRow? {
        val row = findDisplayableProduct(productId) ?: return null
        return CatalogInfo.ProductDetailRow(product = row, detailImages = findProductDetailImages(productId))
    }

    override fun findProductDetailImages(productId: Long): List<String> =
        entityManager.createQuery(
            """
            select image.imageUrl
              from ProductDetailImage image
             where image.deletedAt is null
               and image.productId = :productId
             order by image.sortOrder asc
            """.trimIndent(),
            String::class.java,
        ).setParameter("productId", productId)
            .resultList

    override fun findDisplayableProduct(productId: Long): CatalogInfo.ProductDisplayRow? {
        val query = entityManager.createQuery(
            """
            select p.id, p.name, b.id, b.name, p.price, stats.likeCount, stock.stockQuantity, stock.reservedQuantity
              from Product p, Brand b, ProductStats stats, ProductStock stock
             where p.id = :productId
               and p.deletedAt is null
               and p.status = :productStatus
               and b.deletedAt is null
               and b.status = :brandStatus
               and p.brandId = b.id
               and stats.deletedAt is null
               and stats.productId = p.id
               and stock.deletedAt is null
               and stock.productId = p.id
            """.trimIndent(),
        )
        query.setParameter("productId", productId)
        query.setParameter("productStatus", ProductStatus.ON_SALE)
        query.setParameter("brandStatus", BrandStatus.ACTIVE)
        return query.resultList.firstOrNull()?.let { row ->
            val values = row as Array<*>
            CatalogInfo.ProductDisplayRow(
                productId = values[0] as Long,
                productName = values[1] as String,
                brandId = values[2] as Long,
                brandName = values[3] as String,
                price = values[4] as Long,
                likeCount = values[5] as Long,
                stockQuantity = values[6] as Int,
                reservedQuantity = values[7] as Int,
            )
        }
    }

    private fun validatePage(page: Int, size: Int) {
        if (page < 0 || size <= 0) {
            throw CoreException(ErrorType.BAD_REQUEST, "페이지 요청이 올바르지 않습니다.")
        }
    }

    private fun orderBy(sort: ProductSort): String =
        when (sort) {
            ProductSort.LATEST -> "order by p.createdAt desc"
            ProductSort.PRICE_ASC -> "order by p.price asc, p.createdAt desc"
            ProductSort.LIKES_DESC -> "order by stats.likeCount desc, p.createdAt desc"
        }
}
