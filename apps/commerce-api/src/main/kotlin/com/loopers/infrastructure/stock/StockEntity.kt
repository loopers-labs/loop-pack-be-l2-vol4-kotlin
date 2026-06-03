package com.loopers.infrastructure.stock

import com.loopers.domain.stock.Stock
import com.loopers.persistence.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@SQLRestriction("deleted_at IS NULL")
@Table(
    name = "stock",
    indexes = [Index(name = "uk_stock_product_id", columnList = "product_id", unique = true)],
)
class StockEntity(
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,
) : BaseEntity() {
    fun toDomain(): Stock = Stock(id = id, productId = productId, quantity = quantity)

    fun updateQuantity(quantity: Int) {
        this.quantity = quantity
    }

    companion object {
        fun from(stock: Stock): StockEntity = StockEntity(productId = stock.productId, quantity = stock.quantity)
    }
}
