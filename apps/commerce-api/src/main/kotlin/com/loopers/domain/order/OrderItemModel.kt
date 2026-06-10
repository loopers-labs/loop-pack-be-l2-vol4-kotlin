package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "order_items")
class OrderItemModel(
    productId: Long,
    productName: String,
    price: BigDecimal,
    quantity: Int,
) : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: OrderModel? = null
        protected set

    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    @Column(name = "product_name", nullable = false, length = 200)
    var productName: String = productName
        protected set

    @Column(nullable = false, precision = 10, scale = 2)
    var price: BigDecimal = price
        protected set

    @Column(nullable = false)
    var quantity: Int = quantity
        protected set

    init {
        if (productId <= 0) throw CoreException(ErrorType.BAD_REQUEST, "상품 ID는 양수여야 합니다.")
        if (productName.isBlank()) throw CoreException(ErrorType.BAD_REQUEST, "상품 이름은 비어있을 수 없습니다.")
        if (price <= BigDecimal.ZERO) throw CoreException(ErrorType.BAD_REQUEST, "상품 가격은 0보다 커야 합니다.")
        if (quantity <= 0) throw CoreException(ErrorType.BAD_REQUEST, "주문 수량은 양수여야 합니다.")
    }

    fun assign(order: OrderModel) {
        this.order = order
    }

    fun subtotal(): BigDecimal {
        return price.multiply(BigDecimal(quantity))
    }
}
