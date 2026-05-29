package com.loopers.infrastructure.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Product
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

@Entity
@SQLRestriction("is_deleted = false")
@Table(
    name = "product",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_product_brand_id_name",
            columnNames = ["brand_id", "name"],
        ),
    ],
)
class ProductEntity(
    @Column(name = "brand_id", nullable = false)
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
