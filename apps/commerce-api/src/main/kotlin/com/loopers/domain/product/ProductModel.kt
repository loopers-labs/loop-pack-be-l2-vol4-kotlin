package com.loopers.domain.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.OrderItemModel
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "products")
class ProductModel(
    brandId: Long,
    name: String,
    description: String,
    price: BigDecimal,
    likeCount: Int = 0,
) : BaseEntity() {
    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        protected set

    @Column(nullable = false, length = 200)
    var name: String = name
        protected set

    @Column(nullable = false, columnDefinition = "TEXT")
    var description: String = description
        protected set

    @Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal = price
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Int = likeCount
        protected set

    init {
        validate(brandId = brandId, name = name, price = price, likeCount = likeCount)
    }

    fun incrementLikeCount() {
        likeCount += 1
    }

    fun decrementLikeCount() {
        if (likeCount > 0) likeCount -= 1
    }

    fun softDelete() {
        delete()
    }

    fun isDeleted(): Boolean {
        return deletedAt != null
    }

    fun toOrderItem(quantity: Int): OrderItemModel {
        return OrderItemModel(
            productId = id,
            productName = name,
            price = price,
            quantity = quantity,
        )
    }

    companion object {
        private fun validate(
            brandId: Long,
            name: String,
            price: BigDecimal,
            likeCount: Int,
        ) {
            if (brandId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "브랜드 ID는 양수여야 합니다.")
            if (name.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품 이름은 비어있을 수 없습니다.")
            if (name.length > 200) throw CoreException(ErrorType.BAD_REQUEST, "상품 이름은 200자를 초과할 수 없습니다.")
            if (price <= BigDecimal.ZERO) throw CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0보다 커야 합니다.")
            if (likeCount < 0) throw CoreException(ErrorType.BAD_REQUEST, "좋아요 수는 음수일 수 없습니다.")
        }
    }
}
