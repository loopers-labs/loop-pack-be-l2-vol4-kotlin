package com.loopers.infrastructure.productstat

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product_stat")
class ProductStat(
    @Column(nullable = false, unique = true)
    var productId: Long,

    @Column(nullable = false)
    var likeCount: Long,
) : BaseEntity() {
    fun update(domain: com.loopers.domain.productstat.ProductStat) {
        productId = domain.productId
        likeCount = domain.likeCount
    }
}
