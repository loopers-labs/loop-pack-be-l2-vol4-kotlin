package com.loopers.infrastructure.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Product
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "product")
class ProductEntity(
    @Column(nullable = false)
    var brandId: Long,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var price: Long,

    @Column(nullable = false)
    var description: String,

    @Column(nullable = false)
    var imageUrl: String,

    @Column(nullable = false)
    var isDeleted: Boolean = false,
) : BaseEntity() {
    fun update(domain: Product) {
        brandId = domain.brandId
        name = domain.name
        price = domain.price
        description = domain.description
        imageUrl = domain.imageUrl
        isDeleted = domain.isDeleted
    }
}
