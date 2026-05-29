package com.loopers.infrastructure.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductPrice
import com.loopers.domain.product.Stock
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_products_brand_id", columnList = "brand_id"),
        Index(name = "idx_products_like_count", columnList = "like_count"),
    ],
)
class ProductJpaEntity(
    brandId: Long,
    name: String,
    description: String,
    price: Long,
    stock: Int,
    likeCount: Int,
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

    @Column(name = "stock", nullable = false)
    var stock: Int = stock
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = likeCount
        protected set

    fun apply(product: Product) {
        name = product.name
        description = product.description
        price = product.price.amount
        stock = product.stock.value
        likeCount = product.likeCount
    }

    fun toDomain(): Product {
        return Product(
            id = id,
            brandId = brandId,
            name = name,
            description = description,
            price = ProductPrice(price),
            stock = Stock(stock),
            likeCount = likeCount,
        )
    }

    companion object {
        fun from(product: Product): ProductJpaEntity {
            return ProductJpaEntity(
                brandId = product.brandId,
                name = product.name,
                description = product.description,
                price = product.price.amount,
                stock = product.stock.value,
                likeCount = product.likeCount,
            )
        }
    }
}
