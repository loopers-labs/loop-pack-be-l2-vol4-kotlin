package com.loopers.infrastructure.product

import com.loopers.domain.product.Product
import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "product",
    indexes = [Index(name = "idx_product_brand_id", columnList = "brand_id")],
)
class ProductEntity(
    @Column(name = "name", nullable = false)
    var name: String,

    @Column(name = "price", nullable = false)
    var price: Long,

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    var description: String,

    @Column(name = "brand_id", nullable = false)
    val brandId: Long,
) : BaseEntity() {
    fun toDomain(): Product = Product(
        id = id,
        name = name,
        price = price,
        description = description,
        brandId = brandId,
    )

    fun update(name: String, price: Long, description: String) {
        this.name = name
        this.price = price
        this.description = description
    }

    companion object {
        fun from(product: Product): ProductEntity = ProductEntity(
            name = product.name,
            price = product.price,
            description = product.description,
            brandId = product.brandId,
        )
    }
}
