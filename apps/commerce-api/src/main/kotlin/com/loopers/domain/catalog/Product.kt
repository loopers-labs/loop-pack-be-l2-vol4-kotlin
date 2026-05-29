package com.loopers.domain.catalog

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table

@Entity
@Table(name = "products")
class Product(
    @Column(name = "brand_id", nullable = false)
    val brandId: Long,

    @Column(name = "name", nullable = false, length = 150)
    var name: String,

    @Column(name = "price", nullable = false)
    var price: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ProductStatus = ProductStatus.ON_SALE,
) : BaseEntity() {
    init {
        validateName(name)
        validatePrice(price)
    }

    fun change(name: String, price: Long) {
        validateName(name)
        validatePrice(price)
        this.name = name
        this.price = price
    }

    fun activate() {
        status = ProductStatus.ON_SALE
    }

    fun suspend() {
        status = ProductStatus.SUSPENDED
    }

    private fun validateName(name: String) {
        if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품 이름은 비어있을 수 없습니다.")
    }

    private fun validatePrice(price: Long) {
        if (price <= 0) throw CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0보다 커야 합니다.")
    }
}
