package com.loopers.domain.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.shared.Money
import com.loopers.support.error.BadRequestException
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "order_item")
class OrderItem(
    order: Order,
    productId: Long,
    brandId: Long?,
    productName: String,
    brandName: String?,
    unitPrice: Money,
    quantity: Int,
) : BaseEntity() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    val order: Order = order

    @Column(name = "product_id", nullable = false, updatable = false)
    val productId: Long = productId

    @Column(name = "brand_id", updatable = false)
    val brandId: Long? = brandId

    @Column(name = "product_name", nullable = false, updatable = false)
    val productName: String = productName

    @Column(name = "brand_name", updatable = false)
    val brandName: String? = brandName

    @Embedded
    @AttributeOverride(name = "amount", column = Column(name = "unit_price", nullable = false, updatable = false))
    val unitPrice: Money = unitPrice

    @Column(name = "quantity", nullable = false, updatable = false)
    val quantity: Int = quantity

    init {
        if (quantity < 1) {
            throw BadRequestException(OrderErrorCode.INVALID_ORDER_QUANTITY)
        }
    }

    fun subtotal(): Money = Money(unitPrice.amount * quantity)
}
