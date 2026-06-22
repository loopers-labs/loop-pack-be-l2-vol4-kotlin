package com.loopers.infrastructure.product.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.model.ProductStat
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "product_stat",
    indexes = [
        Index(
            name = "idx_product_stat_like_count_product_id",
            columnList = "like_count, product_id",
        ),
        Index(
            name = "idx_product_stat_brand_like_count_product_id",
            columnList = "brand_id, like_count, product_id",
        ),
    ],
)
class ProductStatEntity(
    @Column(nullable = false, unique = true)
    var productId: Long,

    @Column(name = "brand_id", nullable = false)
    var brandId: Long,

    @Column(nullable = false)
    var likeCount: Long,
) : BaseEntity() {
    fun update(domain: ProductStat) {
        productId = domain.productId
        brandId = domain.brandId
        likeCount = domain.likeCount
    }
}
