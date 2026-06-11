package com.loopers.application.catalog

import com.loopers.application.catalog.port.CatalogProductQueryPort
import com.loopers.application.catalog.port.LikeProductQueryPort
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ProductQueryFacade(
    private val catalogProductQueryPort: CatalogProductQueryPort,
    private val likeProductQueryPort: LikeProductQueryPort?,
) {
    @Transactional(readOnly = true)
    fun getProducts(sort: ProductSort, page: Int, size: Int, userId: Long?): List<CatalogInfo.ProductDisplayInfo> =
        toDisplayInfos(catalogProductQueryPort.findDisplayableProducts(sort, page, size), userId)

    @Transactional(readOnly = true)
    fun getBrandProducts(
        brandId: Long,
        sort: ProductSort,
        page: Int,
        size: Int,
        userId: Long?,
    ): List<CatalogInfo.ProductDisplayInfo> =
        toDisplayInfos(catalogProductQueryPort.findDisplayableProductsByBrandId(brandId, sort, page, size), userId)

    private fun toDisplayInfos(
        rows: List<CatalogInfo.ProductDisplayRow>,
        userId: Long?,
    ): List<CatalogInfo.ProductDisplayInfo> {
        val likedProductIds = if (userId == null || likeProductQueryPort == null) {
            emptySet()
        } else {
            likeProductQueryPort.getLikedProductIds(userId, rows.map { it.productId })
        }
        return rows.map { row ->
            row.toDisplayInfo(
                likedByMe = likedProductIds.contains(row.productId),
                soldOut = row.availableQuantity <= 0,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getProductDetail(productId: Long, userId: Long?): CatalogInfo.ProductDetailInfo {
        val row = catalogProductQueryPort.findDisplayableProductDetail(productId)
            ?: throw CoreException(ErrorType.NOT_FOUND, "상품을 찾을 수 없습니다.")
        return CatalogInfo.ProductDetailInfo(
            product = row.product.toDisplayInfo(
                likedByMe = userId != null && likeProductQueryPort?.isLiked(userId, productId) == true,
                soldOut = row.product.availableQuantity <= 0,
            ),
            detailImages = row.detailImages,
        )
    }

    private fun CatalogInfo.ProductDisplayRow.toDisplayInfo(
        likedByMe: Boolean,
        soldOut: Boolean,
    ) = CatalogInfo.ProductDisplayInfo(
        productId = productId,
        productName = productName,
        brandId = brandId,
        brandName = brandName,
        price = price,
        likeCount = likeCount,
        likedByMe = likedByMe,
        soldOut = soldOut,
    )
}
