package com.loopers.infrastructure.product.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.ProductStat
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "product_stat",
    indexes = [
        Index(name = "uk_product_stat_product_id", columnList = "product_id", unique = true),
        Index(name = "idx_product_stat_like_count_product_id", columnList = "like_count, product_id"),
    ],
)
class ProductStatEntity(
    @Column(name = "product_id", nullable = false, unique = true)
    var productId: Long,

    @Column(name = "brand_id", nullable = false)
    var brandId: Long,

    @Column(name = "like_count", nullable = false)
    var likeCount: Long,

    @Column(name = "sales_count", nullable = false)
    var salesCount: Long,

    @Column(name = "view_count", nullable = false)
    var viewCount: Long,

    @Column(name = "latest_event_version", nullable = false)
    var latestEventVersion: Long = 0L,
) : BaseEntity() {
    fun update(domain: ProductStat) {
        productId = domain.productId
        brandId = domain.brandId
        likeCount = domain.likeCount
        salesCount = domain.salesCount
        viewCount = domain.viewCount
        latestEventVersion = domain.latestEventVersion
    }

    fun toDomain(): ProductStat {
        return ProductStat(
            id = id,
            productId = productId,
            brandId = brandId,
            likeCount = likeCount,
            salesCount = salesCount,
            viewCount = viewCount,
            latestEventVersion = latestEventVersion,
        )
    }
}
