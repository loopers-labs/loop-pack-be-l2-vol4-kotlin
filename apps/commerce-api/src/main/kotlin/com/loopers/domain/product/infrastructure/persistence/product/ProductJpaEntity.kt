package com.loopers.domain.product.infrastructure.persistence.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.model.ProductModel
import com.loopers.domain.product.model.ProductSaleType
import com.loopers.domain.product.vo.Money
import com.loopers.domain.product.vo.ProductName
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "products")
class ProductJpaEntity(
    @Column(name = "brand_id", nullable = false)
    var brandId: Long,
    @Column(name = "product_name", nullable = false)
    var productName: String,
    @Column(nullable = false)
    var price: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "sale_type", nullable = false)
    var saleType: ProductSaleType = ProductSaleType.NORMAL,
) : BaseEntity() {
    fun updateFrom(product: ProductModel) {
        brandId = product.brandId
        productName = product.name.value
        price = product.price.value
        saleType = product.saleType
        if (product.deletedAt == null) {
            restore()
        } else {
            delete()
        }
    }

    fun toDomain(): ProductModel = ProductModel(
        id = id,
        brandId = brandId,
        name = ProductName.of(productName),
        price = Money.of(price),
        saleType = saleType,
        deletedAt = deletedAt,
    )

    companion object {
        fun fromDomain(product: ProductModel): ProductJpaEntity = ProductJpaEntity(
            brandId = product.brandId,
            productName = product.name.value,
            price = product.price.value,
            saleType = product.saleType,
        )
    }
}
