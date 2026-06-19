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
        return findProductPageByBrandId(brandId = brandId, sort = sort, page = page, size = size).toDisplayRows()
    }

    override fun findDisplayableProductDetail(productId: Long): CatalogInfo.ProductDetailRow? {
        val row = findDisplayableProductsById(productId) ?: return null
        val imageUrls = entityManager.createQuery(
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
        return CatalogInfo.ProductDetailRow(product = row, detailImages = imageUrls)
    }

    private fun findDisplayableProductsById(productId: Long): CatalogInfo.ProductDisplayRow? {
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

    private fun findProductPageByBrandId(brandId: Long, sort: ProductSort, page: Int, size: Int): List<ProductPageRow> {
        val query = entityManager.createQuery(
            """
            select p.id, p.name, p.brandId, p.price, stats.likeCount
              from Product p, ProductStats stats
             where p.deletedAt is null
               and p.status = :productStatus
               and p.brandId = :brandId
               and stats.deletedAt is null
               and stats.productId = p.id
             ${orderBy(sort)}
            """.trimIndent(),
        )
        query.setParameter("productStatus", ProductStatus.ON_SALE)
        query.setParameter("brandId", brandId)
        query.firstResult = page * size
        query.maxResults = size

        return query.resultList.map { row ->
            val values = row as Array<*>
            ProductPageRow(
                productId = values[0] as Long,
                productName = values[1] as String,
                brandId = values[2] as Long,
                price = values[3] as Long,
                likeCount = values[4] as Long,
            )
        }
    }

    private fun List<ProductPageRow>.toDisplayRows(): List<CatalogInfo.ProductDisplayRow> {
        if (isEmpty()) return emptyList()

        val brandNames = findBrandNames(map { it.brandId }.distinct())
        val stocks = findStocks(map { it.productId })
        return mapNotNull { row ->
            val brandName = brandNames[row.brandId] ?: return@mapNotNull null
            val stock = stocks[row.productId] ?: return@mapNotNull null
            CatalogInfo.ProductDisplayRow(
                productId = row.productId,
                productName = row.productName,
                brandId = row.brandId,
                brandName = brandName,
                price = row.price,
                likeCount = row.likeCount,
                stockQuantity = stock.stockQuantity,
                reservedQuantity = stock.reservedQuantity,
            )
        }
    }

    private fun findBrandNames(brandIds: Collection<Long>): Map<Long, String> {
        val rows = entityManager.createQuery(
            """
            select b.id, b.name
              from Brand b
             where b.deletedAt is null
               and b.status = :brandStatus
               and b.id in :brandIds
            """.trimIndent(),
        )
            .setParameter("brandStatus", BrandStatus.ACTIVE)
            .setParameter("brandIds", brandIds)
            .resultList

        return rows.associate { row ->
            val values = row as Array<*>
            values[0] as Long to values[1] as String
        }
    }

    private fun findStocks(productIds: Collection<Long>): Map<Long, StockRow> {
        val rows = entityManager.createQuery(
            """
            select stock.productId, stock.stockQuantity, stock.reservedQuantity
              from ProductStock stock
             where stock.deletedAt is null
               and stock.productId in :productIds
            """.trimIndent(),
        )
            .setParameter("productIds", productIds)
            .resultList

        return rows.associate { row ->
            val values = row as Array<*>
            val productId = values[0] as Long
            productId to StockRow(
                stockQuantity = values[1] as Int,
                reservedQuantity = values[2] as Int,
            )
        }
    }

    private fun orderBy(sort: ProductSort): String =
        when (sort) {
            ProductSort.LATEST -> "order by p.createdAt desc"
            ProductSort.PRICE_ASC -> "order by p.price asc, p.createdAt desc"
            ProductSort.LIKES_DESC -> "order by stats.likeCount desc, p.createdAt desc"
        }

    private data class ProductPageRow(
        val productId: Long,
        val productName: String,
        val brandId: Long,
        val price: Long,
        val likeCount: Long,
    )

    private data class StockRow(
        val stockQuantity: Int,
        val reservedQuantity: Int,
    )
}
