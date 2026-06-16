package com.loopers.infrastructure.product.entity

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.model.ProductStat
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_stat")
class ProductStatEntity(
    @Column(nullable = false, unique = true)
    var productId: Long,

    @Column(nullable = false)
    var likeCount: Long,
) : BaseEntity() {
    fun update(domain: ProductStat) {
        productId = domain.productId
        likeCount = domain.likeCount
    }
}
