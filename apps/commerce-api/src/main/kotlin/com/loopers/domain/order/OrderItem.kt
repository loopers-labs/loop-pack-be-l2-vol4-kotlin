package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "order_items")
class OrderItem(
    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "product_name_snapshot", nullable = false, length = 150)
    val productNameSnapshot: String,

    @Column(name = "brand_name_snapshot", nullable = false, length = 150)
    val brandNameSnapshot: String,

    @Column(name = "price_snapshot", nullable = false)
    val priceSnapshot: Long,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,
) : BaseEntity() {
    init {
        if (productNameSnapshot.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품명 스냅샷은 비어있을 수 없습니다.")
        if (brandNameSnapshot.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "브랜드명 스냅샷은 비어있을 수 없습니다.")
        if (priceSnapshot <= 0) throw CoreException(ErrorType.BAD_REQUEST, "가격 스냅샷은 0보다 커야 합니다.")
        if (quantity <= 0) throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 0보다 커야 합니다.")
    }
}
