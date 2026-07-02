package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_products_deleted_created", columnList = "deleted_at, created_at"),
        Index(name = "idx_products_brand_deleted_price", columnList = "brand_id, deleted_at, price"),
        Index(name = "idx_products_deleted_likecount", columnList = "deleted_at, like_count, created_at"),
        Index(name = "idx_products_brand_deleted_likecount", columnList = "brand_id, deleted_at, like_count, created_at"),
    ],
)
class ProductModel(
    name: String,
    price: Long,
    stock: Long,
    brandId: Long,
) : BaseEntity() {

    @Column(name = "name", nullable = false)
    var name: String = name
        protected set

    @Column(name = "price", nullable = false)
    var price: Long = price
        protected set

    @Column(name = "stock", nullable = false)
    var stock: Long = stock
        protected set

    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = 0
        protected set

    init {
        if (name.isBlank()) {
            throw CoreException(errorType = ErrorType.BAD_REQUEST, customMessage = "상품 이름은 비어있을 수 없습니다.")
        }
        if (price < 0) {
            throw CoreException(errorType = ErrorType.BAD_REQUEST, customMessage = "상품 가격은 0 이상이어야 합니다.")
        }
        if (stock < 0) {
            throw CoreException(errorType = ErrorType.BAD_REQUEST, customMessage = "재고는 0 이상이어야 합니다.")
        }
    }

    fun decreaseStock(quantity: Long) {
        if (quantity <= 0) {
            throw CoreException(errorType = ErrorType.BAD_REQUEST, customMessage = "차감 수량은 0보다 커야 합니다.")
        }
        if (stock < quantity) {
            throw CoreException(errorType = ErrorType.BAD_REQUEST, customMessage = "재고가 부족합니다. 현재 재고: $stock")
        }
        stock -= quantity
    }

    fun increaseStock(quantity: Long) {
        if (quantity <= 0) {
            throw CoreException(errorType = ErrorType.BAD_REQUEST, customMessage = "복원 수량은 0보다 커야 합니다.")
        }
        stock += quantity
    }

    fun update(name: String, price: Long, stock: Long) {
        if (name.isBlank()) {
            throw CoreException(errorType = ErrorType.BAD_REQUEST, customMessage = "상품 이름은 비어있을 수 없습니다.")
        }
        if (price < 0) {
            throw CoreException(errorType = ErrorType.BAD_REQUEST, customMessage = "상품 가격은 0 이상이어야 합니다.")
        }
        if (stock < 0) {
            throw CoreException(errorType = ErrorType.BAD_REQUEST, customMessage = "재고는 0 이상이어야 합니다.")
        }
        this.name = name
        this.price = price
        this.stock = stock
    }

    fun isDeleted(): Boolean = deletedAt != null
}
