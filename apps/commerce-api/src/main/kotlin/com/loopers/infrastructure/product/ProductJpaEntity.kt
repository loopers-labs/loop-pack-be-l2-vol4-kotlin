package com.loopers.infrastructure.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductPrice
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_products_brand_id", columnList = "brand_id"),
    ],
)
class ProductJpaEntity(
    brandId: Long,
    name: String,
    description: String,
    price: Long,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false)
    val brandId: Long = brandId

    @Column(name = "name", nullable = false, length = 100)
    var name: String = name
        protected set

    @Column(name = "description", nullable = false, length = 1000)
    var description: String = description
        protected set

    @Column(name = "price", nullable = false)
    var price: Long = price
        protected set

    fun updateFrom(product: Product) {
        name = product.name
        description = product.description
        price = product.price.amount
    }

    fun toDomain(): Product {
        return Product(
            id = id,
            brandId = brandId,
            name = name,
            description = description,
            price = ProductPrice(price),
        )
    }

    companion object {
        fun from(product: Product): ProductJpaEntity {
            return ProductJpaEntity(
                brandId = product.brandId,
                name = product.name,
                description = product.description,
                price = product.price.amount,
            )
        }
    }
}
